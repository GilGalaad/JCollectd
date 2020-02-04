package engine.collect;

import common.exception.ExecutionException;
import engine.sample.CpuRawSample;
import engine.sample.DiskRawSample;
import engine.sample.LoadRawSample;
import engine.sample.MemRawSample;
import engine.sample.NetRawSample;
import engine.sample.GpuRawSample;

public interface CollectStrategy {

    LoadRawSample collectLoadAvg() throws ExecutionException;

    CpuRawSample collectCpu() throws ExecutionException;

    MemRawSample collectMem() throws ExecutionException;

    NetRawSample collectNet(String interfaceName) throws ExecutionException;

    DiskRawSample collectDisk(String deviceName) throws ExecutionException;

    GpuRawSample collectGpu() throws ExecutionException;

}
