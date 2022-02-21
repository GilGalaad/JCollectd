package jcollectd.engine.sample;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class MemRawSample extends ProbeRawSample {

    private long memUsed;
    private long swapUsed;
    private long cacheUsed;

}
