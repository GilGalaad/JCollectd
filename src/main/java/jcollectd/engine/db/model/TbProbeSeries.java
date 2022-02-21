package jcollectd.engine.db.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@ToString
public class TbProbeSeries {

    private String hostname;
    private String probeType;
    private String device;
    private Date sampleTms;
    private BigDecimal sampleValue;

    public TbProbeSeries(String hostname, String probeType, Date sampleTms, BigDecimal sampleValue) {
        this.hostname = hostname;
        this.probeType = probeType;
        this.sampleTms = sampleTms;
        this.sampleValue = sampleValue;
    }

    public TbProbeSeries(String hostname, String probeType, String device, Date sampleTms, BigDecimal sampleValue) {
        this.hostname = hostname;
        this.probeType = probeType;
        this.device = device;
        this.sampleTms = sampleTms;
        this.sampleValue = sampleValue;
    }

}
