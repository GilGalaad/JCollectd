package engine.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.nio.file.Path;
import java.util.ArrayList;

@Getter
@Setter
@ToString
public class CollectConfiguration {

    public static enum OperatingSystem {
        FREEBSD,
        LINUX
    }

    public static enum DbEngine {
        SQLITE
    }

    private OperatingSystem os;
    private DbEngine dbEngine;
    private Path dbPath;
    private Path webPath;
    private String hostname;
    private int reportHours;
    private int retentionHours;
    private int interval;
    private ArrayList<Probe> probeConfigList;

}
