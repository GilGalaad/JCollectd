package engine.sample;

public class CpuRawSample extends ProbeRawSample {

    private long totalTime;
    private long idleTime;

    public long getTotalTime() {
        return totalTime;
    }

    public void setTotalTime(long totalTime) {
        this.totalTime = totalTime;
    }

    public long getIdleTime() {
        return idleTime;
    }

    public void setIdleTime(long idleTime) {
        this.idleTime = idleTime;
    }

    @Override
    public String toString() {
        return "CpuSample{" + "totalTime=" + totalTime + ", idleTime=" + idleTime + '}';
    }

}
