package jcollectd.common.dto.sample;

import lombok.Data;

@Data
public class CpuRawSample extends RawSample {

    private final long totalTime;
    private final long idleTime;

}
