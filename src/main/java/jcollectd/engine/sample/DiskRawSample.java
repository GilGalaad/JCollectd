package jcollectd.engine.sample;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class DiskRawSample extends ProbeRawSample {

    private String device;
    private long readBytes;
    private long writeBytes;

}
