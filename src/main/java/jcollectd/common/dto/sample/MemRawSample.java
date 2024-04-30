package jcollectd.common.dto.sample;

import lombok.Data;

@Data
public class MemRawSample extends RawSample {

    private final long mem;
    private final long cache;
    private final long swap;

}
