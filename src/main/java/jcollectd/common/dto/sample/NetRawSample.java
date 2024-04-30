package jcollectd.common.dto.sample;

import lombok.Data;

@Data
public class NetRawSample extends RawSample {

    private final String device;
    private final long rx;
    private final long tx;

}
