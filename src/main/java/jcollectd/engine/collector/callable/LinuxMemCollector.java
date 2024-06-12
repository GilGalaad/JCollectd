package jcollectd.engine.collector.callable;

import jcollectd.common.dto.sample.MemRawSample;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;

@Log4j2
public class LinuxMemCollector implements Collector {

    @Override
    public MemRawSample call() throws Exception {
        long mem, cache, swap = 0;

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
            mem = memTotal - memAvailable;
            cache = cached + buffers + sReclaimable;
            swap = swapTotal - swapFree;
        }

        /*
            # cat /proc/spl/kstat/zfs/arcstats
            name                            type data
            ...
            size                            4    33650304736
         */
        if (Files.isReadable(Path.of("/proc/spl/kstat/zfs/arcstats"))) {
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/spl/kstat/zfs/arcstats"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("size")) {
                        long arc = Long.parseLong(line.split("\\s+")[2]);
                        // because of how ZFS on Linux is implemented, the ARC memory behaves like cache memory, but is aggregated by the kernel as ordinary memory allocations
                        mem -= arc;
                        cache += arc;
                    }
                }
            }
        }

        MemRawSample ret = new MemRawSample(mem, cache, swap);
        log.debug("Collected sample: {}", ret);
        return ret;
    }

}
