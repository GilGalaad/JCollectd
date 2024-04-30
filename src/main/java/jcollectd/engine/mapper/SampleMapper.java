package jcollectd.engine.mapper;

import jcollectd.common.dto.sample.ComputedSample;
import jcollectd.common.dto.sample.RawSample;

import java.time.Instant;

public interface SampleMapper<T extends RawSample> {

    ComputedSample map(Instant curTms, T curSample, Instant prevTms, T prevSample);

}
