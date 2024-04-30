package jcollectd.engine.collector.runnable;

import jcollectd.common.dto.sample.DiskRawSample;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Stream;

import static jcollectd.common.CommonUtils.processRunner;

@RequiredArgsConstructor
@Log4j2
public class FreeBSDDiskCollector extends CollectorRunnable {

    private final String device;

    @Override
    public DiskRawSample call() throws Exception {
        /*
            # iostat -Ix ada0
                                    extended device statistics
            device           r/i         w/i         kr/i         kw/i qlen   tsvc_t/i      sb/i
            ada0       7919671.0   2973428.0 5905908945.5  119214504.0    0   118260.3   48234.8

            relevant values are in kilobytes
         */
        try {
            List<String> devices = List.of(device.split("\\+"));
            List<String> stdout = processRunner(Stream.concat(Stream.of("iostat", "-Ix"), devices.stream()).toList());
            long read = 0, write = 0;
            for (var line : stdout) {
                String[] split = line.trim().split("\\s+");
                if (!devices.contains(split[0])) {
                    continue;
                }
                read += (new BigDecimal(split[3]).multiply(BigDecimal.valueOf(1024)).setScale(0, RoundingMode.HALF_UP)).longValue();
                write += (new BigDecimal(split[4]).multiply(BigDecimal.valueOf(1024)).setScale(0, RoundingMode.HALF_UP)).longValue();
            }
            DiskRawSample ret = new DiskRawSample(device, read, write);
            log.debug("Collected sample: {}", ret);
            return ret;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while running iostat", ex);
        } catch (Exception ex) {
            throw new RuntimeException("Unexpected error while running iostat", ex);
        }
    }

}
