package engine.collect;

import common.exception.ExecutionException;
import engine.sample.*;

public interface CollectStrategy {

    LoadRawSample collectLoadAvg() throws ExecutionException;

    CpuRawSample collectCpu() throws ExecutionException;

    MemRawSample collectMem() throws ExecutionException;

    NetRawSample collectNet(String interfaceName) throws ExecutionException;

    DiskRawSample collectDisk(String deviceName) throws ExecutionException;

    GpuRawSample collectGpu() throws ExecutionException;

}
