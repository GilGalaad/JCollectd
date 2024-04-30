package jcollectd.engine;

import jcollectd.common.ExceptionUtils;
import jcollectd.common.dto.config.AppConfig;
import jcollectd.common.dto.sample.CollectResult;
import jcollectd.common.dto.sample.RawSample;
import jcollectd.common.exception.CollectException;
import jcollectd.engine.collector.builder.CollectorBuilder;
import jcollectd.engine.collector.builder.FreeBSDCollectorBuilder;
import jcollectd.engine.collector.builder.LinuxCollectorBuilder;
import jcollectd.engine.collector.callable.Collector;
import lombok.extern.log4j.Log4j2;

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
    private List<Collector> collectors;

    // results
    private CollectResult prevResult;
    private CollectResult curResult;

    // timings
    private Instant collectTms;
    private long startTime;
    private Long collectElapsed;

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
                collectTms = getRoundedCurrentInstant();
                log.debug("Collect timestamp set to: {}", DTF.format(collectTms.atZone(ZoneId.of("UTC"))));

                // starting collectors
                startTime = System.nanoTime();
                List<Future<RawSample>> futures = collect();
                collectElapsed = System.nanoTime() - startTime;
                log.debug("Collecting time: {}", smartElapsed(collectElapsed));

                // fetching results
                List<RawSample> rawSamples = fetchResults(futures);
                prevResult = curResult;
                curResult = new CollectResult(collectTms, rawSamples);
            } catch (InterruptedException ex) {
                log.info("Received KILL signal, shutting down");
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private List<Future<RawSample>> collect() throws InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return executor.invokeAll(collectors);
        }
    }

    private List<RawSample> fetchResults(List<Future<RawSample>> futures) {
        ArrayList<RawSample> ret = new ArrayList<>(futures.size());
        boolean failures = false;
        for (int i = 0; i < futures.size(); i++) {
            var future = futures.get(i);
            switch (future.state()) {
                case SUCCESS -> ret.add(future.resultNow());
                case FAILED -> {
                    log.error("Probe #{} failed with following exception: {}", i, ExceptionUtils.getCanonicalForm(future.exceptionNow()));
                    failures = true;
                }
            }
        }
        if (failures) {
            log.error("One or more probes failed, shutting down");
            throw new CollectException();
        }
        return ret;
    }

}
