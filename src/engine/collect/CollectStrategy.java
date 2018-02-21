package engine.collect;

import engine.sample.CpuRawSample;
import engine.sample.DiskRawSample;
import engine.sample.LoadRawSample;
import engine.sample.MemRawSample;
import engine.sample.NetRawSample;

public abstract class CollectStrategy {

    public abstract LoadRawSample collectLoadAvg();

    public abstract CpuRawSample collectCpu();

    public abstract MemRawSample collectMem();

    public abstract NetRawSample collectNet(String interfaceName);

    public abstract DiskRawSample collectDisk(String deviceName);

}
