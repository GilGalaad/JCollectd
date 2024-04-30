package jcollectd.engine.mapper;

import jcollectd.common.dto.sample.ComputedSample;
import jcollectd.common.dto.sample.MemComputedSample;
import jcollectd.common.dto.sample.MemRawSample;

import java.math.BigDecimal;
import java.time.Instant;

import static java.math.RoundingMode.HALF_UP;

public class MemSampleMapper implements SampleMapper<MemRawSample> {

    @Override
    public ComputedSample map(Instant curTms, MemRawSample curSample, Instant prevTms, MemRawSample prevSample) {
        // convert to MiB
        return new MemComputedSample(curTms,
                BigDecimal.valueOf(curSample.getMem()).divide(BigDecimal.valueOf(1024L * 1024L), 0, HALF_UP),
                BigDecimal.valueOf(curSample.getCache()).divide(BigDecimal.valueOf(1024L * 1024L), 0, HALF_UP),
                BigDecimal.valueOf(curSample.getSwap()).divide(BigDecimal.valueOf(1024L * 1024L), 0, HALF_UP));
    }

}
