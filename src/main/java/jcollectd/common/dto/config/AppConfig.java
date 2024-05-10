package jcollectd.common.dto.config;

import lombok.Data;

import java.time.Duration;
import java.util.List;

@Data
public class AppConfig {

    private final OperatingSystem os;
    private final String hostname;
    private final Duration interval;
    private final Duration retention;
    private final int port;
    private final List<Probe> probes;

}
