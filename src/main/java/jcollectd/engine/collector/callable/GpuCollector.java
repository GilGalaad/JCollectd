package jcollectd.engine.collector.callable;

import jcollectd.common.dto.sample.GpuRawSample;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static jcollectd.common.CommonUtils.processRunner;

@AllArgsConstructor
@Log4j2
public class GpuCollector implements Collector {

    @Override
    public GpuRawSample call() throws Exception {
        /*
            # nvidia-smi --format=csv,noheader,nounits --query-gpu=utilization.gpu
            0
            0
         */
        List<String> stdout = processRunner(List.of("nvidia-smi", "--format=csv,noheader,nounits", "--query-gpu=utilization.gpu"));
        List<BigDecimal> augends = stdout.stream().filter(i -> !i.isBlank()).map(BigDecimal::new).toList();
        if (augends.isEmpty()) {
            return new GpuRawSample(BigDecimal.ZERO);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (var augend : augends) {
            sum = sum.add(augend);
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(augends.size()), 1, RoundingMode.HALF_UP);
        GpuRawSample ret = new GpuRawSample(avg);
        log.debug("Collected sample: {}", ret);
        return ret;
    }

}
