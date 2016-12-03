package engine.samples;

import java.math.BigDecimal;

public class LoadSample extends ProbeSample {

    private BigDecimal load1minute;
    private BigDecimal load5minute;
    private BigDecimal load15minute;

    public BigDecimal getLoad1minute() {
        return load1minute;
    }

    public void setLoad1minute(BigDecimal load1minute) {
        this.load1minute = load1minute;
    }

    public BigDecimal getLoad5minute() {
        return load5minute;
    }

    public void setLoad5minute(BigDecimal load5minute) {
        this.load5minute = load5minute;
    }

    public BigDecimal getLoad15minute() {
        return load15minute;
    }

    public void setLoad15minute(BigDecimal load15minute) {
        this.load15minute = load15minute;
    }

    @Override
    public String toString() {
        return "LoadSample{" + "load1minute=" + load1minute + ", load5minute=" + load5minute + ", load15minute=" + load15minute + '}';
    }

}
