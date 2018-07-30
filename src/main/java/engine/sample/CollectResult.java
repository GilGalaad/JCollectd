package engine.sample;

import java.util.ArrayList;
import java.util.Date;

public class CollectResult {

    private Date collectTms;
    private ArrayList<ProbeRawSample> probeRawSampleList;

    public CollectResult(int sz) {
        collectTms = new Date();
        probeRawSampleList = new ArrayList<>(sz);
    }

    public Date getCollectTms() {
        return collectTms;
    }

    public void setCollectTms(Date collectTms) {
        this.collectTms = collectTms;
    }

    public ArrayList<ProbeRawSample> getProbeRawSampleList() {
        return probeRawSampleList;
    }

    public void setProbeRawSampleList(ArrayList<ProbeRawSample> probeRawSampleList) {
        this.probeRawSampleList = probeRawSampleList;
    }

}
