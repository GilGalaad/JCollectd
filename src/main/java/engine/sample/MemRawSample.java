package engine.sample;

public class MemRawSample extends ProbeRawSample {

    private long memUsed;
    private long swapUsed;
    private long cacheUsed;

    public long getMemUsed() {
        return memUsed;
    }

    public void setMemUsed(long memUsed) {
        this.memUsed = memUsed;
    }

    public long getSwapUsed() {
        return swapUsed;
    }

    public void setSwapUsed(long swapUsed) {
        this.swapUsed = swapUsed;
    }

    public long getCacheUsed() {
        return cacheUsed;
    }

    public void setCacheUsed(long cacheUsed) {
        this.cacheUsed = cacheUsed;
    }

    @Override
    public String toString() {
        return "MemRawSample{" + "memUsed=" + memUsed + ", swapUsed=" + swapUsed + ", cacheUsed=" + cacheUsed + '}';
    }

}
