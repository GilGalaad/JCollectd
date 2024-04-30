package jcollectd.common.dto.config;

import lombok.Data;

@Data
public class Probe {

    private final ProbeType type;
    private final ChartSize size;
    private final String device;
    private final String label;

    public Probe(ProbeType type, ChartSize size) {
        this.type = type;
        this.size = size;
        this.device = null;
        this.label = null;
    }

    public Probe(ProbeType type, ChartSize size, String device, String label) {
        this.type = type;
        this.size = size;
        this.device = device;
        this.label = label;
    }

    public String prettyPrint() {
        return switch (type) {
            case LOAD, CPU, MEM, GPU -> String.format("type: %s, size: %s", type, size);
            case NET, DISK, ZFS -> String.format("type: %s, size: %s, device: %s, label: %s", type, size, device, label);
        };
    }

}
