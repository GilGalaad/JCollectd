package jcollectd.common.dto.config;

import java.util.Arrays;

public enum ChartSize {
    FULL,
    HALF;

    public static ChartSize of(String s) {
        if (s == null) {
            return null;
        }
        return Arrays.stream(values()).filter(i -> i.name().equalsIgnoreCase(s.trim())).findFirst().orElse(null);
    }
}
