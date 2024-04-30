package jcollectd.common.dto.sample;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class LoadComputedSample extends ComputedSample {

    private final Instant sampleTms;
    private final BigDecimal load1;
    private final BigDecimal load5;
    private final BigDecimal load15;

}
