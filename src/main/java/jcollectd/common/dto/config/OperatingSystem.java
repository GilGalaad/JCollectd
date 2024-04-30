package jcollectd.common.dto.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OperatingSystem {
    LINUX("Linux"),
    FREEBSD("FreeBSD");

    private final String label;

}
