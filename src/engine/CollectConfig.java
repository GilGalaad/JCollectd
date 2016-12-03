package engine;

import java.nio.file.Path;
import java.util.ArrayList;

public class CollectConfig {

    private Path dbPath;
    private Path webPath;
    private String hostname;
    private int reportHours;
    private int retentionHours;
    private ArrayList<ProbeConfig> probeConfList;

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

    public ArrayList<ProbeConfig> getProbeConfList() {
        return probeConfList;
    }

    public void setProbeConfList(ArrayList<ProbeConfig> probeConfList) {
        this.probeConfList = probeConfList;
    }

}
