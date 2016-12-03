package engine.samples;

public class HddSample extends ProbeSample {

    private String deviceName;
    private long readBytes;
    private long writeBytes;

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public long getReadBytes() {
        return readBytes;
    }

    public void setReadBytes(long readBytes) {
        this.readBytes = readBytes;
    }

    public long getWriteBytes() {
        return writeBytes;
    }

    public void setWriteBytes(long writeBytes) {
        this.writeBytes = writeBytes;
    }

    @Override
    public String toString() {
        return "HddSample{" + "deviceName=" + deviceName + ", readBytes=" + readBytes + ", writeBytes=" + writeBytes + '}';
    }

}
