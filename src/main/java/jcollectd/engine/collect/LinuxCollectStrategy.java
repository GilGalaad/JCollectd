package jcollectd.engine.collect;

import jcollectd.common.exception.ExecutionException;
import jcollectd.engine.sample.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static jcollectd.common.CommonUtils.isEmpty;

public class LinuxCollectStrategy implements CollectStrategy {

    @Override
    public LoadRawSample collectLoadAvg() throws ExecutionException {
        LoadRawSample ret = new LoadRawSample();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/loadavg"))) {
            String[] split = br.readLine().split("\\s+");
            ret.setLoad1minute(new BigDecimal(split[0]));
            ret.setLoad5minute(new BigDecimal(split[1]));
            ret.setLoad15minute(new BigDecimal(split[2]));
        } catch (IOException ex) {
            throw new ExecutionException("Unexpected error while reading /proc virtual filesystem", ex);
        }
        return ret;
    }

    @Override
    public CpuRawSample collectCpu() throws ExecutionException {
        CpuRawSample ret = new CpuRawSample();
        // since the first word of line is 'cpu', numbers start from split[1]
        // 4th value is idle, 5th is iowait
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            String[] split = br.readLine().split("\\s+");
            long total = 0;
            for (int i = 1; i < split.length; i++) {
                total += Long.parseLong(split[i]);
            }
            ret.setTotalTime(total);
            ret.setIdleTime(Long.parseLong(split[4]) + Long.parseLong(split[5]));
        } catch (IOException ex) {
            throw new ExecutionException("Unexpected error while reading /proc virtual filesystem", ex);
        }
        return ret;
    }

    @Override
    public MemRawSample collectMem() throws ExecutionException {
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
            ret.setCacheUsed(cached);
        } catch (IOException ex) {
            throw new ExecutionException("Unexpected error while reading /proc virtual filesystem", ex);
        }
        return ret;
    }

    @Override
    public NetRawSample collectNet(String device) throws ExecutionException {
        NetRawSample ret = new NetRawSample();
        ret.setDevice(device);
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/net/dev"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith(device)) {
                    continue;
                }
                String[] split = line.split("\\s+");
                ret.setRxBytes(Long.parseLong(split[1]));
                ret.setTxBytes(Long.parseLong(split[9]));
                break;
            }
        } catch (IOException ex) {
            throw new ExecutionException("Unexpected error while reading /proc virtual filesystem", ex);
        }
        return ret;
    }

    @Override
    public DiskRawSample collectDisk(String device) throws ExecutionException {
        DiskRawSample ret = new DiskRawSample();
        ret.setDevice(device);
        String[] devList = device.split("\\+");
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
            throw new ExecutionException("Unexpected error while reading /proc virtual filesystem", ex);
        }
        ret.setReadBytes(readBytes);
        ret.setWriteBytes(writeBytes);
        return ret;
    }

    @Override
    public DiskRawSample collectZFS(String device) throws ExecutionException {
        throw new ExecutionException("ZFS probe type not supported on this platform");
    }

    @Override
    public GpuRawSample collectGpu() throws ExecutionException {
        GpuRawSample ret = new GpuRawSample();
        List<BigDecimal> loads = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("sh", "-c", "nvidia-smi --format=csv,noheader,nounits --query-gpu=utilization.gpu").redirectErrorStream(true).start();
            p.waitFor();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    loads.add(new BigDecimal(line.trim()));
                }
            }
        } catch (IOException | InterruptedException ex) {
            throw new ExecutionException("Unexpected error while running nvidia-smi", ex);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal load : loads) {
            sum = sum.add(load);
        }
        ret.setLoad(sum.divide(BigDecimal.valueOf(loads.size()), 1, RoundingMode.HALF_UP));
        return ret;
    }

}
