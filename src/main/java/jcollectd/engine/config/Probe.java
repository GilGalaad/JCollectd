package jcollectd.engine.config;

import lombok.Getter;
import lombok.Setter;

import static jcollectd.common.CommonUtils.isEmpty;

@Getter
@Setter
public class Probe {

    private final ProbeType probeType;
    private final ChartSize chartSize;
    private final String device;
    private final String label;

    public Probe(ProbeType probeType, ChartSize chartSize) {
        this.probeType = probeType;
        this.chartSize = chartSize;
        this.device = null;
        this.label = null;
    }

    public Probe(ProbeType probeType, ChartSize chartSize, String device, String label) {
        this.probeType = probeType;
        this.chartSize = chartSize;
        this.device = device;
        this.label = !isEmpty(label) ? label : device;
    }

    @Override
    public String toString() {
        if (device == null) {
            return String.format("%s, %s", probeType, chartSize);
        } else {
            return String.format("%s, %s, %s, %s", probeType, chartSize, device, label);
        }
    }

}
