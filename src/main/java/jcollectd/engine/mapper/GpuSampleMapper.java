package jcollectd.engine.mapper;

import jcollectd.common.dto.sample.ComputedSample;
import jcollectd.common.dto.sample.GpuComputedSample;
import jcollectd.common.dto.sample.GpuRawSample;

import java.time.Instant;

public class GpuSampleMapper implements SampleMapper<GpuRawSample> {

    @Override
    public ComputedSample map(Instant curTms, GpuRawSample curSample, Instant prevTms, GpuRawSample prevSample) {
        return new GpuComputedSample(curTms, curSample.getLoad());
    }

}
