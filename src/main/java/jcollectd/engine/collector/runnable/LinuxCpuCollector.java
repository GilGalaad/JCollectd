package jcollectd.engine.collector.runnable;

import jcollectd.common.dto.sample.CpuRawSample;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;

@Log4j2
public class LinuxCpuCollector extends CollectorRunnable {

    @Override
    public CpuRawSample call() throws Exception {
        /*
            # cat /proc/stat
            cpu  137 24 4986 51841700 99228 0 1756 0 0 0

            values are: user, nice, system, idle, iowait, irq, softirq
         */
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            String[] split = br.readLine().trim().split("\\s+");
            long totalTime = Arrays.stream(split).skip(1).mapToLong(Long::parseLong).sum();
            long idleTime = Long.parseLong(split[4]) + Long.parseLong(split[5]);
            CpuRawSample ret = new CpuRawSample(totalTime, idleTime);
            log.debug("Collected sample: {}", ret);
            return ret;
        } catch (Exception ex) {
            throw new RuntimeException("Unexpected error while reading /proc virtual filesystem", ex);
        }
    }

}
