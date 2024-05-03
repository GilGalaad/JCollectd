package jcollectd.common.dto.rest;

import jcollectd.common.dto.config.Probe;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class Runtime {

    private final String hostname;
    private final Long interval;
    private final List<Probe> probes;
    private final Instant collectTms;
    private final String collectElapsed;
    private final String persistElapsed;
    private final String reportElapsed;
    private final List<List<Object[]>> datasets;

}
