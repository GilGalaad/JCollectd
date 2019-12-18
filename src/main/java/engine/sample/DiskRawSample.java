package engine.sample;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @ToString
public class DiskRawSample extends ProbeRawSample {

    private String deviceName;
    private long readBytes;
    private long writeBytes;

}
