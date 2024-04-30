package jcollectd.engine.mapper;

import jcollectd.common.dto.sample.ComputedSample;
import jcollectd.common.dto.sample.NetComputedSample;
import jcollectd.common.dto.sample.NetRawSample;

import java.math.BigDecimal;
import java.time.Instant;

import static java.math.RoundingMode.HALF_UP;

public class NetSampleMapper implements SampleMapper<NetRawSample> {

    @Override
    public ComputedSample map(Instant curTms, NetRawSample curSample, Instant prevTms, NetRawSample prevSample) {
        long diffRx = curSample.getRx() - prevSample.getRx();
        long diffTx = curSample.getTx() - prevSample.getTx();
        long elapsedMilliseconds = curTms.toEpochMilli() - prevTms.toEpochMilli();
        // convert to Mbps
        BigDecimal rx = elapsedMilliseconds > 0 ? BigDecimal.valueOf(diffRx * 1000L * 8L).divide(BigDecimal.valueOf(elapsedMilliseconds * 1000L * 1000L), 1, HALF_UP) : BigDecimal.ZERO;
        BigDecimal tx = elapsedMilliseconds > 0 ? BigDecimal.valueOf(diffTx * 1000L * 8L).divide(BigDecimal.valueOf(elapsedMilliseconds * 1000L * 1000L), 1, HALF_UP) : BigDecimal.ZERO;
        return new NetComputedSample(curTms, curSample.getDevice(), rx, tx);
    }

}
