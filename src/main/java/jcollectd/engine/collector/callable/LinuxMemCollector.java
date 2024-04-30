package jcollectd.engine.collector.callable;

import jcollectd.common.dto.sample.MemRawSample;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.FileReader;

@Log4j2
public class LinuxMemCollector extends Collector {

    @Override
    public MemRawSample call() throws Exception {
        /*
            # cat /proc/meminfo
            MemTotal:        3992412 kB
            MemAvailable:    3463416 kB
            Buffers:            1348 kB
            Cached:            78740 kB
            SwapTotal:       1048572 kB
            SwapFree:        1048572 kB
            SReclaimable:      18284 kB

            values are reported in kilobytes but actually in kibibyte
         */
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            long memTotal = 0, memAvailable = 0, buffers = 0, cached = 0, sReclaimable = 0, swapTotal = 0, swapFree = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemTotal")) {
                    memTotal = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                } else if (line.startsWith("MemAvailable")) {
                    memAvailable = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                } else if (line.startsWith("Buffers")) {
                    buffers = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                } else if (line.startsWith("Cached")) {
                    cached = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                } else if (line.startsWith("SReclaimable")) {
                    sReclaimable = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                } else if (line.startsWith("SwapTotal")) {
                    swapTotal = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                } else if (line.startsWith("SwapFree")) {
                    swapFree = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                }
            }
            long mem = memTotal - memAvailable;
            long cache = cached + buffers + sReclaimable;
            long swap = swapTotal - swapFree;
            MemRawSample ret = new MemRawSample(mem, cache, swap);
            log.debug("Collected sample: {}", ret);
            return ret;
        }
    }

}
