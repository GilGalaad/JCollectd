package jcollectd.engine.collector.builder;

import jcollectd.engine.collector.callable.*;

public class FreeBSDCollectorBuilder implements CollectorBuilder {

    @Override
    public Collector buildLoadCollector() {
        return new FreeBSDLoadCollector();
    }

    @Override
    public Collector buildCpuCollector() {
        return new FreeBSDCpuCollector();
    }

    @Override
    public Collector buildMemCollector() {
        return new FreeBSDMemCollector();
    }

    @Override
    public Collector buildNetCollector(String device) {
        return new FreeBSDNetCollector(device);
    }

    @Override
    public Collector buildDiskCollector(String device) {
        return new FreeBSDDiskCollector(device);
    }

    @Override
    public Collector buildZfsCollector(String device) {
        return new FreeBSDZfsCollector(device);
    }

    @Override
    public Collector buildGpuCollector() {
        return new GpuCollector();
    }

}
