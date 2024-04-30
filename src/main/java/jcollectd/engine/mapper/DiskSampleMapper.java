package jcollectd.engine.mapper;

import jcollectd.common.dto.sample.ComputedSample;
import jcollectd.common.dto.sample.DiskComputedSample;
import jcollectd.common.dto.sample.DiskRawSample;

import java.math.BigDecimal;
import java.time.Instant;

import static java.math.RoundingMode.HALF_UP;

public class DiskSampleMapper implements SampleMapper<DiskRawSample> {

    @Override
    public ComputedSample map(Instant curTms, DiskRawSample curSample, Instant prevTms, DiskRawSample prevSample) {
        long diffRead = curSample.getRead() - prevSample.getRead();
        long diffWrite = curSample.getWrite() - prevSample.getWrite();
        long elapsedMilliseconds = curTms.toEpochMilli() - prevTms.toEpochMilli();
        // convert to MiB/s
        BigDecimal read = elapsedMilliseconds > 0 ? BigDecimal.valueOf(diffRead * 1000L).divide(BigDecimal.valueOf(elapsedMilliseconds * 1024L * 1024L), 1, HALF_UP) : BigDecimal.ZERO;
        BigDecimal write = elapsedMilliseconds > 0 ? BigDecimal.valueOf(diffWrite * 1000L).divide(BigDecimal.valueOf(elapsedMilliseconds * 1024L * 1024L), 1, HALF_UP) : BigDecimal.ZERO;
        return new DiskComputedSample(curTms, curSample.getDevice(), read, write);
    }

}
