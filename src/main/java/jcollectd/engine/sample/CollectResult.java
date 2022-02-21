package jcollectd.engine.sample;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Date;

@Getter
@Setter
@ToString
public class CollectResult {

    private final Date collectTms;
    private final ArrayList<ProbeRawSample> samples;

    public CollectResult(int size) {
        collectTms = new Date();
        samples = new ArrayList<>(size);
    }

}
