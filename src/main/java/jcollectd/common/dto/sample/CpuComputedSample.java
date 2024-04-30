package jcollectd.common.dto.sample;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class CpuComputedSample extends ComputedSample {

    private final Instant sampleTms;
    private final BigDecimal load;

}
