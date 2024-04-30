package jcollectd.engine.collector.runnable;

import jcollectd.common.dto.sample.LoadRawSample;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.util.List;

import static jcollectd.common.CommonUtils.processRunner;

@Log4j2
public class FreeBSDLoadCollector extends CollectorRunnable {

    @Override
    public LoadRawSample call() throws Exception {
        /*
            # sysctl vm.loadavg
            vm.loadavg: { 0.21 0.22 0.17 }
         */
        try {
            List<String> stdout = processRunner(List.of("sysctl", "vm.loadavg"));
            String[] split = stdout.getFirst().trim().split("\\s+");
            BigDecimal load1 = new BigDecimal(split[2]);
            BigDecimal load5 = new BigDecimal(split[3]);
            BigDecimal load15 = new BigDecimal(split[4]);
            LoadRawSample ret = new LoadRawSample(load1, load5, load15);
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
