package jcollectd.engine.sample;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
public class GpuRawSample extends ProbeRawSample {

    private BigDecimal load;

}
