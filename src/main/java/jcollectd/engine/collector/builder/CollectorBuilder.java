package jcollectd.engine.collector.builder;

import jcollectd.engine.collector.runnable.CollectorRunnable;

public interface CollectorBuilder {

    CollectorRunnable buildLoadCollector();

    CollectorRunnable buildCpuCollector();

    CollectorRunnable buildMemCollector();

    CollectorRunnable buildNetCollector(String device);

    CollectorRunnable buildDiskCollector(String device);

    CollectorRunnable buildZfsCollector(String device);

    CollectorRunnable buildGpuCollector();

}
