package engine.sample;

public class MemRawSample extends ProbeRawSample {

    private long memUsed;
    private long swapUsed;

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

    @Override
    public String toString() {
        return "MemSample{" + "memUsed=" + memUsed + ", swapUsed=" + swapUsed + '}';
    }

}
