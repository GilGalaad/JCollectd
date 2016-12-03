package engine.samples;

import java.util.ArrayList;
import java.util.Date;

public class CollectResult {

    private Date collectTms;
    private ArrayList<ProbeSample> probeSampleList;

    public CollectResult(int sz) {
        collectTms = new Date();
        probeSampleList = new ArrayList<>(sz);
    }

    public Date getCollectTms() {
        return collectTms;
    }

    public void setCollectTms(Date collectTms) {
        this.collectTms = collectTms;
    }

    public ArrayList<ProbeSample> getProbeSampleList() {
        return probeSampleList;
    }

    public void setProbeSampleList(ArrayList<ProbeSample> probeSampleList) {
        this.probeSampleList = probeSampleList;
    }

}
