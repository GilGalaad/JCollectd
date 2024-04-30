package jcollectd.engine.collector.callable;

import jcollectd.common.dto.sample.LoadRawSample;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;

@Log4j2
public class LinuxLoadCollector implements Collector {

    @Override
    public LoadRawSample call() throws Exception {
        /*
            # cat /proc/loadavg
            0.34 0.24 0.20 1/144 40571
         */
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/loadavg"))) {
            String[] split = br.readLine().trim().split("\\s+");
            BigDecimal load1 = new BigDecimal(split[0]);
            BigDecimal load5 = new BigDecimal(split[1]);
            BigDecimal load15 = new BigDecimal(split[2]);
            LoadRawSample ret = new LoadRawSample(load1, load5, load15);
            log.debug("Collected sample: {}", ret);
            return ret;
        }
    }

}
