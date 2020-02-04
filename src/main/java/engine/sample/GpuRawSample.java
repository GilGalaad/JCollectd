package engine.sample;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @ToString
public class GpuRawSample extends ProbeRawSample {

    private BigDecimal load;

}
