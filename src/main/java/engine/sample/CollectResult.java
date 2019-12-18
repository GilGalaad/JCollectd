package engine.sample;

import java.util.ArrayList;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @ToString
public class CollectResult {

    private final Date collectTms;
    private final ArrayList<ProbeRawSample> probeRawSampleList;

    public CollectResult(int sz) {
        collectTms = new Date();
        probeRawSampleList = new ArrayList<>(sz);
    }

}
