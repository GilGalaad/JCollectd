package jcollectd.engine.collector.runnable;

import jcollectd.common.dto.sample.NetRawSample;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;

import static jcollectd.common.CommonUtils.processRunner;

@RequiredArgsConstructor
@Log4j2
public class FreeBSDNetCollector extends CollectorRunnable {

    private final String device;

    @Override
    public NetRawSample call() throws Exception {
        /*
            # netstat -b -n -I ix0
            Name    Mtu Network      Address                Ipkts Ierrs Idrop        Ibytes      Opkts Oerrs        Obytes  Coll
            ix0    1500 <Link#1>     80:61:5f:08:94:b8  354890649     0     0  359184917448  420246724     0  446709191018     0
            ix0       - 10.0.0.0/24  10.0.0.2           265005016     -     -  351748830726  659391062     -  808764847758     -
         */
        try {
            List<String> stdout = processRunner(List.of("netstat", "-b", "-n", "-I", device));
            long rx = 0, tx = 0;
            for (var line : stdout) {
                line = line.trim();
                if (!line.startsWith(device) || !line.toLowerCase().contains("link")) {
                    continue;
                }
                String[] split = line.split("\\s+");
                rx = Long.parseLong(split[7]);
                tx = Long.parseLong(split[10]);
                break;
            }
            NetRawSample ret = new NetRawSample(device, rx, tx);
            log.debug("Collected sample: {}", ret);
            return ret;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while running netstat", ex);
        } catch (Exception ex) {
            throw new RuntimeException("Unexpected error while running netstat", ex);
        }
    }

}
