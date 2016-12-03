package engine;

public class ProbeConfig {

    public enum ProbeType {
        LOAD,
        CPU,
        MEM,
        NET,
        BLK
    }

    public enum GraphSize {
        FULL_SIZE,
        HALF_SIZE
    }

    private final ProbeType ptype;
    private final GraphSize gsize;
    private final String deviceName;

    public ProbeConfig(ProbeType ptype, GraphSize gsize) {
        this.ptype = ptype;
        this.gsize = gsize;
        this.deviceName = null;
    }

    public ProbeConfig(ProbeType ptype, GraphSize gsize, String deviceName) {
        this.ptype = ptype;
        this.gsize = gsize;
        this.deviceName = deviceName;
    }

    public ProbeType getPtype() {
        return ptype;
    }

    public GraphSize getGsize() {
        return gsize;
    }

    public String getDeviceName() {
        return deviceName;
    }

}
