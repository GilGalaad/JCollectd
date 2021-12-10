package engine.sample;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class NetRawSample extends ProbeRawSample {

    private String interfaceName;
    private long txBytes;
    private long rxBytes;

}
