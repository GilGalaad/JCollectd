package jcollectd.engine.sample;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class CpuRawSample extends ProbeRawSample {

    private long totalTime;
    private long idleTime;

}
