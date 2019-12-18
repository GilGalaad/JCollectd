package engine.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @ToString
public class ProbeConfiguration {

    public enum ProbeType {
        LOAD,
        CPU,
        MEM,
        NET,
        DISK
    }

    public enum ChartSize {
        FULL_SIZE,
        HALF_SIZE
    }

    private final ProbeType prType;
    private final ChartSize chSize;
    private final String device;
    private final String label;

    public ProbeConfiguration(ProbeType ptype, ChartSize gsize) {
        this.prType = ptype;
        this.chSize = gsize;
        this.device = null;
        this.label = null;
    }

    public ProbeConfiguration(ProbeType ptype, ChartSize gsize, String deviceName, String label) {
        this.prType = ptype;
        this.chSize = gsize;
        this.device = deviceName;
        this.label = label;
    }

}
