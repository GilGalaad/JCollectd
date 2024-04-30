package jcollectd.common.dto.sample;

import lombok.Data;

@Data
public class DiskRawSample extends RawSample {

    private final String device;
    private final long read;
    private final long write;

}
