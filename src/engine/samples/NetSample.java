package engine.samples;

public class NetSample extends ProbeSample {

    private String interfaceName;
    private long rxBytes;
    private long txBytes;

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public long getRxBytes() {
        return rxBytes;
    }

    public void setRxBytes(long rxBytes) {
        this.rxBytes = rxBytes;
    }

    public long getTxBytes() {
        return txBytes;
    }

    public void setTxBytes(long txBytes) {
        this.txBytes = txBytes;
    }

    @Override
    public String toString() {
        return "NetSample{" + "interfaceName=" + interfaceName + ", rxBytes=" + rxBytes + ", txBytes=" + txBytes + '}';
    }

}
