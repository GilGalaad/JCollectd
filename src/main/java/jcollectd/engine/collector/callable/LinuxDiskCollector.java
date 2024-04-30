package jcollectd.engine.collector.callable;

import jcollectd.common.dto.sample.DiskRawSample;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;

@RequiredArgsConstructor
@Log4j2
public class LinuxDiskCollector extends Collector {

    private final String device;

    @Override
    public DiskRawSample call() throws Exception {
        /*
            # cat /proc/diskstats
              11       0 sr0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
               8       0 sda 10605 1020 2201044 4154482028 132213 24 2262362 4153945554 0 31076 4153877826 0 0 0 0 5667 4154459615
               8       1 sda1 115 1020 7312 4154504655 2 0 2 0 0 88 4154504655 0 0 0 0 0 0
               8       2 sda2 86 0 6504 4154504518 0 0 0 0 0 56 4154504518 0 0 0 0 0 0
               8       3 sda3 10309 0 2183724 4154482204 132211 24 2262360 4153945554 0 31040 4153923073 0 0 0 0 0 0

            3rd field is device name, 6th field is sectors read, 10th field is sectors written, one sector is 512 bytes for historical reasons
         */
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/diskstats"))) {
            List<String> devices = List.of(device.split("\\+"));
            long read = 0, write = 0;
            String line;
            while ((line = br.readLine()) != null) {
                String[] split = line.trim().split("\\s+");
                if (!devices.contains(split[2])) {
                    continue;
                }
                read += Long.parseLong(split[5]) * 512L;
                write += Long.parseLong(split[9]) * 512L;
            }
            DiskRawSample ret = new DiskRawSample(device, read, write);
            log.debug("Collected sample: {}", ret);
            return ret;
        }
    }

}
