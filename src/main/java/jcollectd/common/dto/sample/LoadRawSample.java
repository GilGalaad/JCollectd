package jcollectd.common.dto.sample;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoadRawSample extends RawSample {

    private final BigDecimal load1;
    private final BigDecimal load5;
    private final BigDecimal load15;

}
