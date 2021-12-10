package engine.db.model;

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

}
