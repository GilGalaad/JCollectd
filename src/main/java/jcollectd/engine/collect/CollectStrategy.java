package jcollectd.engine.collect;

import jcollectd.common.exception.ExecutionException;
import jcollectd.engine.sample.*;

public interface CollectStrategy {

    LoadRawSample collectLoadAvg() throws ExecutionException;

    CpuRawSample collectCpu() throws ExecutionException;

    MemRawSample collectMem() throws ExecutionException;

    NetRawSample collectNet(String device) throws ExecutionException;

    DiskRawSample collectDisk(String device) throws ExecutionException;

    DiskRawSample collectZFS(String device) throws ExecutionException;

    GpuRawSample collectGpu() throws ExecutionException;

}
