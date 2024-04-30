package jcollectd.engine.collector.builder;

import jcollectd.engine.collector.callable.*;

public class LinuxCollectorBuilder implements CollectorBuilder {

    @Override
    public Collector buildLoadCollector() {
        return new LinuxLoadCollector();
    }

    @Override
    public Collector buildCpuCollector() {
        return new LinuxCpuCollector();
    }

    @Override
    public Collector buildMemCollector() {
        return new LinuxMemCollector();
    }

    @Override
    public Collector buildNetCollector(String device) {
        return new LinuxNetCollector(device);
    }

    @Override
    public Collector buildDiskCollector(String device) {
        return new LinuxDiskCollector(device);
    }

    @Override
    public Collector buildZfsCollector(String device) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collector buildGpuCollector() {
        return new GpuCollector();
    }

}
