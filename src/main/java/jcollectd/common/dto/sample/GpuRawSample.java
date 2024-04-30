package jcollectd.common.dto.sample;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GpuRawSample extends RawSample {

    private final BigDecimal load;

}
