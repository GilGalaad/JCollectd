package jcollectd.common.dto.sample;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class CollectResult {

    private final Instant collectTms;
    private final List<RawSample> rawSamples;

}
