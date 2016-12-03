package engine;

import engine.config.CollectConfig;
import engine.config.ProbeConfig.ProbeType;
import engine.samples.CollectResult;
import engine.samples.CpuSample;
import engine.samples.LoadSample;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import static main.JCollectd.logger;

public class CollectEngine {

    private final CollectConfig conf;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
    private final long SAMPLING_INTERVAL = 6000L;

    private CollectResult prevResult;
    private CollectResult curResult;

    public CollectEngine(CollectConfig conf) {
        this.conf = conf;
    }

    public void run() {
        while (true) {
            // waiting for next schedule
            try {
                Thread.sleep(SAMPLING_INTERVAL - (System.currentTimeMillis() % SAMPLING_INTERVAL));
            } catch (InterruptedException ex) {
                logger.info("Received KILL signal, exiting from main loop");
                return;
            }

            // making room for new samples
            prevResult = curResult;
            curResult = new CollectResult(conf.getProbeConfigList().size());

            for (int i = 0; i < conf.getProbeConfigList().size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Received KILL signal, exiting from main loop");
                    return;
                }
                if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.LOAD) {
                    curResult.getProbeSampleList().add(i, parseLoadAvg());
                    logger.info(curResult.getProbeSampleList().get(i).toString());
                } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.CPU) {
                    curResult.getProbeSampleList().add(i, parseCpu());
                    logger.info(curResult.getProbeSampleList().get(i).toString());
                }
            }
        }
    }

    private LoadSample parseLoadAvg() {
        LoadSample load = new LoadSample();
        if (System.getProperty("os.name").equals("Linux")) {
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/loadavg"))) {
                String line = br.readLine();
                String[] split = line.split("\\s+");
                load.setLoad1minute(new BigDecimal(split[0]));
                load.setLoad5minute(new BigDecimal(split[1]));
                load.setLoad15minute(new BigDecimal(split[2]));
            } catch (IOException ex) {
                // can't happen
            }
        }
        return load;
    }

    private CpuSample parseCpu() {
        CpuSample cpu = new CpuSample();
        if (System.getProperty("os.name").equals("Linux")) {
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
                String line = br.readLine();
                String[] split = line.split("\\s+");
                long total = 0;
                for (int i = 1; i < split.length; i++) {
                    total += Long.parseLong(split[i]);
                }
                cpu.setTotalTime(total);
                cpu.setIdleTime(Long.parseLong(split[4]) + Long.parseLong(split[5]));
            } catch (IOException ex) {
                // can't happen
            }
        }
        return cpu;
    }
}
