package engine.collect;

import static engine.CommonUtils.isEmpty;
import engine.sample.CpuRawSample;
import engine.sample.DiskRawSample;
import engine.sample.LoadRawSample;
import engine.sample.MemRawSample;
import engine.sample.NetRawSample;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;

public class LinuxCollectStrategy extends CollectStrategy {

    @Override
    public LoadRawSample collectLoadAvg() {
        LoadRawSample load = new LoadRawSample();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/loadavg"))) {
            String[] split = br.readLine().split("\\s+");
            load.setLoad1minute(new BigDecimal(split[0]));
            load.setLoad5minute(new BigDecimal(split[1]));
            load.setLoad15minute(new BigDecimal(split[2]));
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Unexpected %s while reading /proc virtual filesystem, aborting - %s", ex.getClass().getSimpleName(), ex.getMessage()), ex);
        }
        return load;
    }

    @Override
    public CpuRawSample collectCpu() {
        CpuRawSample cpu = new CpuRawSample();
        // since the first word of line is 'cpu', numbers start from split[1]
        // 4th value is idle, 5th is iowait
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            String[] split = br.readLine().split("\\s+");
            long total = 0;
            for (int i = 1; i < split.length; i++) {
                total += Long.parseLong(split[i]);
            }
            cpu.setTotalTime(total);
            cpu.setIdleTime(Long.parseLong(split[4]) + Long.parseLong(split[5]));
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Unexpected %s while reading /proc virtual filesystem, aborting - %s", ex.getClass().getSimpleName(), ex.getMessage()), ex);
        }
        return cpu;
    }

    @Override
    public MemRawSample collectMem() {
        MemRawSample ret = new MemRawSample();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            long memTotal = 0, memFree = 0, buffers = 0, cached = 0, swapTotal = 0, swapFree = 0;
            // values from /proc are in kibibyte, but we store in bytes
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemTotal")) {
                    memTotal = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                } else if (line.startsWith("MemFree")) {
                    memFree = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                } else if (line.startsWith("Buffers")) {
                    buffers = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                } else if (line.startsWith("Cached")) {
                    cached = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                } else if (line.startsWith("SwapTotal")) {
                    swapTotal = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                } else if (line.startsWith("SwapFree")) {
                    swapFree = Long.parseLong(line.split("\\s+")[1]) * 1024L;
                }
            }
            ret.setMemUsed(memTotal - memFree - buffers - cached);
            ret.setSwapUsed(swapTotal - swapFree);
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Unexpected %s while reading /proc virtual filesystem, aborting - %s", ex.getClass().getSimpleName(), ex.getMessage()), ex);
        }
        return ret;
    }

    @Override
    public NetRawSample collectNet(String interfaceName) {
        NetRawSample ret = new NetRawSample();
        ret.setInterfaceName(interfaceName);
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
                break;
            }
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Unexpected %s while reading /proc virtual filesystem, aborting - %s", ex.getClass().getSimpleName(), ex.getMessage()), ex);
        }
        return ret;
    }

    @Override
    public DiskRawSample collectDisk(String deviceName) {
        DiskRawSample ret = new DiskRawSample();
        ret.setDeviceName(deviceName);
        String[] devList = deviceName.split("\\+");
        long readBytes = 0, writeBytes = 0;
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/diskstats"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                String[] split = line.split("\\s+");
                for (String dev : devList) {
                    if (!isEmpty(dev) && split[2].equals(dev.trim())) {
                        // values in 512 bytes sectors
                        readBytes += Long.parseLong(split[2 + 3]) * 512L;
                        writeBytes += Long.parseLong(split[2 + 7]) * 512L;
                    }
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Unexpected %s while reading /proc virtual filesystem, aborting - %s", ex.getClass().getSimpleName(), ex.getMessage()), ex);
        }
        ret.setReadBytes(readBytes);
        ret.setWriteBytes(writeBytes);
        return ret;
    }

}
