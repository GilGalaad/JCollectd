package jcollectd.common.dto.sample;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class MemComputedSample extends ComputedSample {

    private final Instant sampleTms;
    private final BigDecimal mem;
    private final BigDecimal cache;
    private final BigDecimal swap;

}
