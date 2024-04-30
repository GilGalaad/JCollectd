package jcollectd.engine.collector.builder;

import jcollectd.engine.collector.runnable.*;

public class LinuxCollectorBuilder implements CollectorBuilder {

    @Override
    public CollectorRunnable buildLoadCollector() {
        return new LinuxLoadCollector();
    }

    @Override
    public CollectorRunnable buildCpuCollector() {
        return new LinuxCpuCollector();
    }

    @Override
    public CollectorRunnable buildMemCollector() {
        return new LinuxMemCollector();
    }

    @Override
    public CollectorRunnable buildNetCollector(String device) {
        return new LinuxNetCollector(device);
    }

    @Override
    public CollectorRunnable buildDiskCollector(String device) {
        return new LinuxDiskCollector(device);
    }

    @Override
    public CollectorRunnable buildZfsCollector(String device) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CollectorRunnable buildGpuCollector() {
        return new GpuCollector();
    }

}
