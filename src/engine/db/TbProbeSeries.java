package engine.db;

import java.math.BigDecimal;
import java.util.Date;

public class TbProbeSeries {

    private String hostname;
    private String probeType;
    private String device;
    private Date sampleTms;
    private BigDecimal sampleValue;

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getProbeType() {
        return probeType;
    }

    public void setProbeType(String probeType) {
        this.probeType = probeType;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public Date getSampleTms() {
        return sampleTms;
    }

    public void setSampleTms(Date sampleTms) {
        this.sampleTms = sampleTms;
    }

    public BigDecimal getSampleValue() {
        return sampleValue;
    }

    public void setSampleValue(BigDecimal sampleValue) {
        this.sampleValue = sampleValue;
    }

    @Override
    public String toString() {
        return "TbProbeSeries{" + "hostname=" + hostname + ", probeType=" + probeType + ", device=" + device + ", sampleTms=" + sampleTms + ", sampleValue=" + sampleValue + '}';
    }

}
