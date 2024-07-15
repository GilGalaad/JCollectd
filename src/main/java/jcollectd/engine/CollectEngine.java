package jcollectd.engine;

import jcollectd.common.ExceptionUtils;
import jcollectd.common.dto.config.AppConfig;
import jcollectd.common.dto.sample.*;
import jcollectd.common.exception.CollectException;
import jcollectd.engine.collector.builder.CollectorBuilder;
import jcollectd.engine.collector.builder.FreeBSDCollectorBuilder;
import jcollectd.engine.collector.builder.LinuxCollectorBuilder;
import jcollectd.engine.collector.callable.Collector;
import jcollectd.engine.mapper.*;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static jcollectd.common.CommonUtils.getRoundedCurrentInstant;
import static jcollectd.common.CommonUtils.smartElapsed;

@Log4j2
public class CollectEngine {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss z");

    // configuration
    private final AppConfig config;
    private final long interval;
    private final List<Collector> collectors;

    // results
    @Getter
    private volatile CollectResult curResult;
    private volatile CollectResult prevResult;

    // timings
    @Getter
    private volatile Long collectElapsed;
    @Getter
    private volatile Long persistElapsed;

    public CollectEngine(AppConfig config) {
        this.config = config;
        interval = config.getInterval().toMillis();

        CollectorBuilder collectorBuilder = switch (config.getOs()) {
            case LINUX -> new LinuxCollectorBuilder();
            case FREEBSD -> new FreeBSDCollectorBuilder();
        };

        collectors = new ArrayList<>(config.getProbes().size());
        for (var probe : config.getProbes()) {
            switch (probe.getType()) {
                case LOAD -> collectors.add(collectorBuilder.buildLoadCollector());
                case CPU -> collectors.add(collectorBuilder.buildCpuCollector());
                case MEM -> collectors.add(collectorBuilder.buildMemCollector());
                case NET -> collectors.add(collectorBuilder.buildNetCollector(probe.getDevice()));
                case DISK -> collectors.add(collectorBuilder.buildDiskCollector(probe.getDevice()));
                case ZFS -> collectors.add(collectorBuilder.buildZfsCollector(probe.getDevice()));
                case GPU -> collectors.add(collectorBuilder.buildGpuCollector());
            }
        }
    }

    public void run() throws CollectException {
        log.info("Entering main loop");
        while (true) {
            try {
                // waiting for next schedule
                Thread.sleep(interval - (Instant.now().toEpochMilli() % interval));

                // calculating collect timestamp rounded to the nearest second
                Instant collectTms = getRoundedCurrentInstant();
                log.debug("Collect timestamp set to: {}", DTF.format(collectTms.atZone(ZoneId.of("UTC"))));

                // starting collectors
                long startTime = System.nanoTime();
                List<Future<RawSample>> futures = runCollectors();
                List<RawSample> rawSamples = getResults(futures);
                collectElapsed = System.nanoTime() - startTime;
                log.debug("Collecting time: {}", smartElapsed(collectElapsed));

                // moving observation window
                prevResult = curResult;
                curResult = new CollectResult(collectTms, rawSamples);
                if (prevResult == null) {
                    continue;
                }

                // mapping raw samples into computed samples, eventually comparing with previous result
                List<ComputedSample> computedSamples = mapSamples();

                // persisting samples
                startTime = System.nanoTime();
                persistSamples(computedSamples);
                persistElapsed = System.nanoTime() - startTime;
                log.debug("Persisting time: {}", smartElapsed(persistElapsed));
            } catch (InterruptedException ex) {
                log.info("Received KILL signal, shutting down");
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private List<Future<RawSample>> runCollectors() throws InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return executor.invokeAll(collectors);
        }
    }

    private List<RawSample> getResults(List<Future<RawSample>> futures) {
        ArrayList<RawSample> ret = new ArrayList<>(config.getProbes().size());
        boolean failures = false;
        for (int i = 0; i < futures.size(); i++) {
            var future = futures.get(i);
            switch (future.state()) {
                case SUCCESS -> ret.add(future.resultNow());
                case FAILED -> {
                    log.error("Collector #{} failed: {}", i, ExceptionUtils.getCanonicalForm(future.exceptionNow()));
                    failures = true;
                }
            }
        }
        if (failures) {
            log.error("One or more collectors failed, shutting down");
            throw new CollectException();
        }
        return ret;
    }

    private List<ComputedSample> mapSamples() {
        List<ComputedSample> ret = new ArrayList<>(config.getProbes().size());
        for (int i = 0; i < config.getProbes().size(); i++) {
            try {
                switch (config.getProbes().get(i).getType()) {
                    case LOAD -> ret.add(new LoadSampleMapper().map(curResult.getCollectTms(), (LoadRawSample) curResult.getRawSamples().get(i), prevResult.getCollectTms(), (LoadRawSample) prevResult.getRawSamples().get(i)));
                    case CPU -> ret.add(new CpuSampleMapper().map(curResult.getCollectTms(), (CpuRawSample) curResult.getRawSamples().get(i), prevResult.getCollectTms(), (CpuRawSample) prevResult.getRawSamples().get(i)));
                    case MEM -> ret.add(new MemSampleMapper().map(curResult.getCollectTms(), (MemRawSample) curResult.getRawSamples().get(i), prevResult.getCollectTms(), (MemRawSample) prevResult.getRawSamples().get(i)));
                    case NET -> ret.add(new NetSampleMapper().map(curResult.getCollectTms(), (NetRawSample) curResult.getRawSamples().get(i), prevResult.getCollectTms(), (NetRawSample) prevResult.getRawSamples().get(i)));
                    case DISK, ZFS -> ret.add(new DiskSampleMapper().map(curResult.getCollectTms(), (DiskRawSample) curResult.getRawSamples().get(i), prevResult.getCollectTms(), (DiskRawSample) prevResult.getRawSamples().get(i)));
                    case GPU -> ret.add(new GpuSampleMapper().map(curResult.getCollectTms(), (GpuRawSample) curResult.getRawSamples().get(i), prevResult.getCollectTms(), (GpuRawSample) prevResult.getRawSamples().get(i)));
                }
            } catch (Exception ex) {
                log.error("Mapping data from collector #{} failed: {}", i, ExceptionUtils.getCanonicalForm(ex));
                throw new CollectException();
            }
        }
        ret.forEach(log::debug);
        return ret;
    }

    private void persistSamples(List<ComputedSample> samples) {
        try (var service = new SqliteService()) {
            service.initializeDatabase();
            service.persistSamples(samples, curResult.getCollectTms(), curResult.getCollectTms().minus(config.getRetention()));
        } catch (SQLException ex) {
            log.error("Persisting data failed: {}", ExceptionUtils.getCanonicalFormWithStackTrace(ex));
            throw new CollectException();
        }
    }

}
