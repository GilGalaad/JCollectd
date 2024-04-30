package jcollectd.engine.collector.callable;

import jcollectd.common.dto.sample.MemRawSample;
import lombok.extern.log4j.Log4j2;

import java.util.List;

import static jcollectd.common.CommonUtils.processRunner;

@Log4j2
public class FreeBSDMemCollector implements Collector {

    @Override
    public MemRawSample call() throws Exception {
        long mem, cache, swap = 0;

        /*
            # sysctl vm.stats.vm.v_page_size vm.stats.vm.v_wire_count vm.stats.vm.v_active_count vm.stats.vm.v_inactive_count vm.stats.vm.v_laundry_count vm.stats.vm.v_cache_count vfs.bufspace kstat.zfs.misc.arcstats.size
            vm.stats.vm.v_page_size: 4096
            vm.stats.vm.v_wire_count: 7409941
            vm.stats.vm.v_active_count: 50829
            vm.stats.vm.v_inactive_count: 92430
            vm.stats.vm.v_laundry_count: 106561
            vm.stats.vm.v_cache_count: 0
            vfs.bufspace: 0
            kstat.zfs.misc.arcstats.size: 27319539336

            sysctl should always return a number of rows equal to the number of values requested, even in case of unknown oid
            values from sysctl are in pages, usually 4096 bytes each, except bufspace and arc which are in bytes, ZFS module could be not loaded
         */
        List<String> stdout1 = processRunner(List.of("sysctl", "vm.stats.vm.v_page_size", "vm.stats.vm.v_active_count", "vm.stats.vm.v_inactive_count", "vm.stats.vm.v_laundry_count", "vm.stats.vm.v_wire_count", "vm.stats.vm.v_cache_count", "vfs.bufspace", "kstat.zfs.misc.arcstats.size"));
        long pageSize = 0, wireCount = 0, activeCount = 0, inactiveCount = 0, laundryCount = 0, cacheCount = 0, bufspace = 0, arc = 0;
        for (var line : stdout1) {
            if (line.startsWith("vm.stats.vm.v_page_size")) {
                pageSize = Long.parseLong(line.split("\\s+")[1]);
            } else if (line.startsWith("vm.stats.vm.v_active_count")) {
                activeCount = Long.parseLong(line.split("\\s+")[1]);
            } else if (line.startsWith("vm.stats.vm.v_inactive_count")) {
                inactiveCount = Long.parseLong(line.split("\\s+")[1]);
            } else if (line.startsWith("vm.stats.vm.v_laundry_count")) {
                laundryCount = Long.parseLong(line.split("\\s+")[1]);
            } else if (line.startsWith("vm.stats.vm.v_wire_count")) {
                wireCount = Long.parseLong(line.split("\\s+")[1]);
            } else if (line.startsWith("vm.stats.vm.v_cache_count")) {
                cacheCount = Long.parseLong(line.split("\\s+")[1]);
            } else if (line.startsWith("vfs.bufspace")) {
                bufspace = Long.parseLong(line.split("\\s+")[1]);
            } else if (line.startsWith("kstat.zfs.misc.arcstats.size") && !line.contains("unknown oid")) {
                arc = Long.parseLong(line.split("\\s+")[1]);
            }
        }
        mem = activeCount * pageSize + inactiveCount * pageSize + laundryCount * pageSize + wireCount * pageSize - arc;
        cache = cacheCount * pageSize + bufspace + arc;


        /*
            # swapinfo -k
            Device          1K-blocks     Used    Avail Capacity
            /dev/mirror/swap  16777212        0 16777212     0%

            relevant value is in kibibytes
         */
        List<String> stdout2 = processRunner(List.of("swapinfo", "-k"));
        for (var line : stdout2) {
            line = line.trim();
            if (line.toLowerCase().startsWith("device")) {
                continue;
            }
            String[] split = line.split("\\s+");
            swap += Long.parseLong(split[2]) * 1024L;
        }

        MemRawSample ret = new MemRawSample(mem, cache, swap);
        log.debug("Collected sample: {}", ret);
        return ret;
    }

}
