package jcollectd.engine.mapper;

import jcollectd.common.dto.sample.ComputedSample;
import jcollectd.common.dto.sample.CpuComputedSample;
import jcollectd.common.dto.sample.CpuRawSample;

import java.math.BigDecimal;
import java.time.Instant;

import static java.math.RoundingMode.HALF_UP;

public class CpuSampleMapper implements SampleMapper<CpuRawSample> {

    @Override
    public ComputedSample map(Instant curTms, CpuRawSample curSample, Instant prevTms, CpuRawSample prevSample) {
        long diffTotal = curSample.getTotalTime() - prevSample.getTotalTime();
        long diffIdle = curSample.getIdleTime() - prevSample.getIdleTime();
        // counters should be monotonic and diffTotal should never be 0, but better safe than sorry
        BigDecimal load = diffTotal > 0 ? BigDecimal.valueOf((diffTotal - diffIdle) * 100).divide(BigDecimal.valueOf(diffTotal), 1, HALF_UP) : BigDecimal.ZERO;
        return new CpuComputedSample(curTms, load);
    }

}
