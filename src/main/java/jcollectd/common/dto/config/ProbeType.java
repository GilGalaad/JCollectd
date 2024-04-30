package jcollectd.common.dto.config;

import java.util.Arrays;

public enum ProbeType {
    LOAD,
    CPU,
    MEM,
    NET,
    DISK,
    ZFS,
    GPU;

    public static ProbeType of(String s) {
        if (s == null) {
            return null;
        }
        return Arrays.stream(values()).filter(i -> i.name().equalsIgnoreCase(s.trim())).findFirst().orElse(null);
    }
}
