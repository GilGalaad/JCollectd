package engine;

import engine.config.CollectConfig;
import engine.config.ProbeConfig.ProbeType;
import engine.samples.CollectResult;
import engine.samples.CpuSample;
import engine.samples.HddSample;
import engine.samples.LoadSample;
import engine.samples.MemSample;
import engine.samples.NetSample;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import static main.JCollectd.logger;
import static main.JCollectd.prettyPrint;

public class CollectEngine {

    private final CollectConfig conf;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
    private final long SAMPLING_INTERVAL = 2000L;

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

            // collecting samples
            long startCollectTime = System.nanoTime();
            for (int i = 0; i < conf.getProbeConfigList().size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Received KILL signal, exiting from main loop");
                    return;
                }
                if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.LOAD) {
                    curResult.getProbeSampleList().add(i, parseLoadAvg());
                } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.CPU) {
                    curResult.getProbeSampleList().add(i, parseCpu());
                } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.MEM) {
                    curResult.getProbeSampleList().add(i, parseMem());
                } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.NET) {
                    curResult.getProbeSampleList().add(i, parseNet(conf.getProbeConfigList().get(i).getDeviceName()));
                } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.HDD) {
                    curResult.getProbeSampleList().add(i, parseDisk(conf.getProbeConfigList().get(i).getDeviceName()));
                }
                logger.debug(curResult.getProbeSampleList().get(i).toString());
            }
            long endCollectTime = System.nanoTime();
            logger.info("Collecting time: {} msec", prettyPrint((endCollectTime - startCollectTime) / 1000000L));
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
            // since the first word of line is 'cpu', numbers start from split[1]
            // 4th value is idle, 5th is iowait
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

    private MemSample parseMem() {
        MemSample ret = new MemSample();
        if (System.getProperty("os.name").equals("Linux")) {
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
                String line;
                // values in KiB
                long memTotal = 0;
                long memFree = 0;
                long buffers = 0;
                long cached = 0;
                long swapTotal = 0;
                long swapFree = 0;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("MemTotal")) {
                        String[] split = line.split("\\s+");
                        memTotal = Long.parseLong(split[1]);
                    } else if (line.startsWith("MemFree")) {
                        String[] split = line.split("\\s+");
                        memFree = Long.parseLong(split[1]);
                    } else if (line.startsWith("Buffers")) {
                        String[] split = line.split("\\s+");
                        buffers = Long.parseLong(split[1]);
                    } else if (line.startsWith("Cached")) {
                        String[] split = line.split("\\s+");
                        cached = Long.parseLong(split[1]);
                    } else if (line.startsWith("SwapTotal")) {
                        String[] split = line.split("\\s+");
                        swapTotal = Long.parseLong(split[1]);
                    } else if (line.startsWith("SwapFree")) {
                        String[] split = line.split("\\s+");
                        swapFree = Long.parseLong(split[1]);
                    }
                }
                ret.setMemUsed(memTotal - memFree - buffers - cached);
                ret.setSwapUsed(swapTotal - swapFree);
            } catch (IOException ex) {
                // can't happen
            }
        }
        return ret;
    }

    private NetSample parseNet(String interfaceName) {
        NetSample ret = new NetSample();
        ret.setInterfaceName(interfaceName);
        if (System.getProperty("os.name").equals("Linux")) {
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/net/dev"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.startsWith(interfaceName)) {
                        continue;
                    }
                    String[] split = line.split("\\s+");
                    ret.setRxBytes(Long.parseLong(split[1]));
                    ret.setTxBytes(Long.parseLong(split[9]));
                }
            } catch (IOException ex) {
                // can't happen
            }
        }
        return ret;
    }

    private HddSample parseDisk(String deviceName) {
        HddSample ret = new HddSample();
        ret.setDeviceName(deviceName);
        if (System.getProperty("os.name").equals("Linux")) {
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/diskstats"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    String[] split = line.split("\\s+");
                    if (!split[2].equals(deviceName)) {
                        continue;
                    }
                    ret.setReadBytes(Long.parseLong(split[2 + 3]) * 512L);
                    ret.setWriteBytes(Long.parseLong(split[2 + 7]) * 512L);
                }
            } catch (IOException ex) {
                // can't happen
            }
        }
        return ret;
    }

}
