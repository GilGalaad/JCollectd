package engine.sample;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @ToString
public class LoadRawSample extends ProbeRawSample {

    private BigDecimal load1minute;
    private BigDecimal load5minute;
    private BigDecimal load15minute;

}
