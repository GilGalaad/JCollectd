package jcollectd.engine.collector.builder;

import jcollectd.engine.collector.runnable.*;

public class FreeBSDCollectorBuilder implements CollectorBuilder {

    @Override
    public CollectorRunnable buildLoadCollector() {
        return new FreeBSDLoadCollector();
    }

    @Override
    public CollectorRunnable buildCpuCollector() {
        return new FreeBSDCpuCollector();
    }

    @Override
    public CollectorRunnable buildMemCollector() {
        return new FreeBSDMemCollector();
    }

    @Override
    public CollectorRunnable buildNetCollector(String device) {
        return new FreeBSDNetCollector(device);
    }

    @Override
    public CollectorRunnable buildDiskCollector(String device) {
        return new FreeBSDDiskCollector(device);
    }

    @Override
    public CollectorRunnable buildZfsCollector(String device) {
        return new FreeBSDZfsCollector(device);
    }

    @Override
    public CollectorRunnable buildGpuCollector() {
        return new GpuCollector();
    }

}
