package engine.collect;

import engine.sample.CpuRawSample;
import engine.sample.DiskRawSample;
import engine.sample.LoadRawSample;
import engine.sample.MemRawSample;
import engine.sample.NetRawSample;

public interface CollectStrategy {

    LoadRawSample collectLoadAvg();

    CpuRawSample collectCpu();

    MemRawSample collectMem();

    NetRawSample collectNet(String interfaceName);

    DiskRawSample collectDisk(String deviceName);

}
