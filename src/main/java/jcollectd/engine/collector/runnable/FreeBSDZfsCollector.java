package jcollectd.engine.collector.runnable;

import jcollectd.common.dto.sample.DiskRawSample;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;

import static jcollectd.common.CommonUtils.processRunner;

@AllArgsConstructor
@Log4j2
public class FreeBSDZfsCollector extends CollectorRunnable {

    private final String device;

    @Override
    public DiskRawSample call() throws Exception {
        /*
            # sysctl kstat.zfs.zroot.dataset
            kstat.zfs.zroot.dataset.objset-0x15.nread: 39873806244
            kstat.zfs.zroot.dataset.objset-0x15.nwritten: 757338217
         */
        try {
            List<String> stdout = processRunner(List.of("sysctl", "kstat.zfs." + device + ".dataset"));
            long read = 0, write = 0;
            for (var line : stdout) {
                line = line.trim();
                if (!line.startsWith("kstat.zfs." + device + ".dataset") || (!line.contains(".nread:") && !line.contains(".nwritten:"))) {
                    continue;
                }
                String[] split = line.trim().split("\\s+");
                if (line.contains(".nread:")) {
                    read += Long.parseLong(split[1]);
                } else if (line.contains(".nwritten:")) {
                    write += Long.parseLong(split[1]);
                }
            }
            DiskRawSample ret = new DiskRawSample(device, read, write);
            log.debug("Collected sample: {}", ret);
            return ret;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while running sysctl", ex);
        } catch (Exception ex) {
            throw new RuntimeException("Unexpected error while running sysctl", ex);
        }
    }

}
