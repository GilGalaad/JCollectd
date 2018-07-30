package engine.config;

import java.nio.file.Path;
import java.util.ArrayList;

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
    private ArrayList<ProbeConfiguration> probeConfigList;

    public OperatingSystem getOs() {
        return os;
    }

    public void setOs(OperatingSystem os) {
        this.os = os;
    }

    public DbEngine getDbEngine() {
        return dbEngine;
    }

    public void setDbEngine(DbEngine dbEngine) {
        this.dbEngine = dbEngine;
    }

    public Path getDbPath() {
        return dbPath;
    }

    public void setDbPath(Path dbPath) {
        this.dbPath = dbPath;
    }

    public Path getWebPath() {
        return webPath;
    }

    public void setWebPath(Path webPath) {
        this.webPath = webPath;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getReportHours() {
        return reportHours;
    }

    public void setReportHours(int reportHours) {
        this.reportHours = reportHours;
    }

    public int getRetentionHours() {
        return retentionHours;
    }

    public void setRetentionHours(int retentionHours) {
        this.retentionHours = retentionHours;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public ArrayList<ProbeConfiguration> getProbeConfigList() {
        return probeConfigList;
    }

    public void setProbeConfigList(ArrayList<ProbeConfiguration> probeConfigList) {
        this.probeConfigList = probeConfigList;
    }

}
