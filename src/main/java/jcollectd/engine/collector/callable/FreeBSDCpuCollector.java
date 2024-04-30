package jcollectd.engine.collector.callable;

import jcollectd.common.dto.sample.CpuRawSample;
import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.List;

import static jcollectd.common.CommonUtils.processRunner;

@Log4j2
public class FreeBSDCpuCollector implements Collector {

    @Override
    public CpuRawSample call() throws Exception {
        /*
            # sysctl kern.cp_time
            kern.cp_time: 316785 0 8123388 66005 617330599

            values are: user, nice, system, interrupt, idle
         */
        List<String> stdout = processRunner(List.of("sysctl", "kern.cp_time"));
        String[] split = stdout.getFirst().trim().split("\\s+");
        long totalTime = Arrays.stream(split).skip(1).mapToLong(Long::parseLong).sum();
        long idleTime = Long.parseLong(split[5]);
        CpuRawSample ret = new CpuRawSample(totalTime, idleTime);
        log.debug("Collected sample: {}", ret);
        return ret;
    }

}
