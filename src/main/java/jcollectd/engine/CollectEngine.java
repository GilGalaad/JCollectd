package jcollectd.engine;

import jcollectd.common.ExceptionUtils;
import jcollectd.common.dto.config.AppConfig;
import jcollectd.common.dto.sample.CollectResult;
import jcollectd.common.dto.sample.RawSample;
import jcollectd.engine.collector.builder.CollectorBuilder;
import jcollectd.engine.collector.builder.FreeBSDCollectorBuilder;
import jcollectd.engine.collector.builder.LinuxCollectorBuilder;
import jcollectd.engine.collector.runnable.CollectorRunnable;
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
    private List<CollectorRunnable> collectors;

    // results
    private CollectResult prevResult;
    private CollectResult curResult;

    // timings
    private Instant collectTms;
    private Long collectElapsed;

    public CollectEngine(AppConfig config) {
        this.config = config;

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

    public void run() {
        log.info("Entering main loop");

        final long interval = config.getInterval().toMillis();
        while (true) {
            // waiting for next schedule
            try {
                Thread.sleep(interval - (Instant.now().toEpochMilli() % interval));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }

            // calculating collect timestamp rounded to the nearest second
            collectTms = getRoundedCurrentInstant();
            log.debug("Collect timestamp set to: {}", DTF.format(collectTms.atZone(ZoneId.of("UTC"))));

            // starting collectors
            List<Future<RawSample>> futures;
            long startCollectTime = System.nanoTime();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                futures = executor.invokeAll(collectors);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            long endCollectTime = System.nanoTime();
            collectElapsed = endCollectTime - startCollectTime;
            log.debug("Collecting time: {}", smartElapsed(collectElapsed));

            // fetching results
            ArrayList<RawSample> rawSamples = new ArrayList<>(config.getProbes().size());
            for (var future : futures) {
                switch (future.state()) {
                    case SUCCESS -> rawSamples.add(future.resultNow());
                    case FAILED -> {
                        log.error(ExceptionUtils.getCanonicalFormWithStackTrace(future.exceptionNow()));
                        return;
                    }
                }
            }
            prevResult = curResult;
            curResult = new CollectResult(collectTms, rawSamples);
        }

    }

}
