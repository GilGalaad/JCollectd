package jcollectd.engine.mapper;

import jcollectd.common.dto.sample.ComputedSample;
import jcollectd.common.dto.sample.LoadComputedSample;
import jcollectd.common.dto.sample.LoadRawSample;

import java.time.Instant;

public class LoadSampleMapper implements SampleMapper<LoadRawSample> {

    @Override
    public ComputedSample map(Instant curTms, LoadRawSample curSample, Instant prevTms, LoadRawSample prevSample) {
        return new LoadComputedSample(curTms, curSample.getLoad1(), curSample.getLoad5(), curSample.getLoad15());
    }

}
