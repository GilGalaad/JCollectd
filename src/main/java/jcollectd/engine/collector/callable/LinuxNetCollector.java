package jcollectd.engine.collector.callable;

import jcollectd.common.dto.sample.NetRawSample;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.FileReader;

@RequiredArgsConstructor
@Log4j2
public class LinuxNetCollector extends Collector {

    private final String device;

    @Override
    public NetRawSample call() throws Exception {
        /*
            # cat /proc/net/dev
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                lo:       0       0    0    0    0     0          0         0        0       0    0    0    0     0       0          0
            enp0s3:  305024    3127    0    0    0     0          0         0   377114    2893    0    0    0     0       0          0
         */
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/dev/net"))) {
            long rx = 0, tx = 0;
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith(device)) {
                    continue;
                }
                String[] split = line.trim().split("\\s+");
                rx = Long.parseLong(split[1]);
                tx = Long.parseLong(split[9]);
                break;
            }
            NetRawSample ret = new NetRawSample(device, rx, tx);
            log.debug("Collected sample: {}", ret);
            return ret;
        }
    }

}
