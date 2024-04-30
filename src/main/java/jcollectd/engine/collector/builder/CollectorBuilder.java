package jcollectd.engine.collector.builder;

import jcollectd.engine.collector.callable.Collector;

public interface CollectorBuilder {

    Collector buildLoadCollector();

    Collector buildCpuCollector();

    Collector buildMemCollector();

    Collector buildNetCollector(String device);

    Collector buildDiskCollector(String device);

    Collector buildZfsCollector(String device);

    Collector buildGpuCollector();

}
