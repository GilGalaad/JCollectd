package jcollectd.common.dto.sample;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class NetComputedSample extends ComputedSample {

    private final Instant sampleTms;
    private final String device;
    private final BigDecimal rx;
    private final BigDecimal tx;

}
