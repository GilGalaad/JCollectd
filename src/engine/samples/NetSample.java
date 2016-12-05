package engine.samples;

public class NetSample extends ProbeSample {

    private String interfaceName;
    private long txBytes;
    private long rxBytes;

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public long getTxBytes() {
        return txBytes;
    }

    public void setTxBytes(long txBytes) {
        this.txBytes = txBytes;
    }

    public long getRxBytes() {
        return rxBytes;
    }

    public void setRxBytes(long rxBytes) {
        this.rxBytes = rxBytes;
    }

    @Override
    public String toString() {
        return "NetSample{" + "interfaceName=" + interfaceName + ", txBytes=" + txBytes + ", rxBytes=" + rxBytes + '}';
    }

}
