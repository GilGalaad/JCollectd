package jcollectd.engine.collect;

import jcollectd.common.exception.ExecutionException;
import jcollectd.engine.sample.*;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static jcollectd.common.CommonUtils.isEmpty;

@Log4j2
public class FreeBSDCollectStrategy implements CollectStrategy {

    @Override
    public LoadRawSample collectLoadAvg() throws ExecutionException {
        LoadRawSample ret = new LoadRawSample();
        try {
            Process p = new ProcessBuilder("sysctl", "vm.loadavg").redirectErrorStream(true).start();
            p.waitFor();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String[] split = br.readLine().split("\\s+");
                ret.setLoad1minute(new BigDecimal(split[2]));
                ret.setLoad5minute(new BigDecimal(split[3]));
                ret.setLoad15minute(new BigDecimal(split[4]));
            }
        } catch (IOException | InterruptedException ex) {
            throw new ExecutionException("Unexpected error while running sysctl", ex);
        }
        return ret;
    }

    @Override
    public CpuRawSample collectCpu() throws ExecutionException {
        CpuRawSample ret = new CpuRawSample();
        // since the first word of line is always the sysctl name
        // values are: user, nice, system, interrupt, idle
        try {
            Process p = new ProcessBuilder("sysctl", "kern.cp_time").redirectErrorStream(true).start();
            p.waitFor();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String[] split = br.readLine().split("\\s+");
                long total = 0;
                for (int i = 1; i < split.length; i++) {
                    total += Long.parseLong(split[i]);
                }
                ret.setTotalTime(total);
                ret.setIdleTime(Long.parseLong(split[4]) + Long.parseLong(split[5]));
            }
        } catch (IOException | InterruptedException ex) {
            throw new ExecutionException("Unexpected error while running sysctl", ex);
        }
        return ret;
    }

    @Override
    public MemRawSample collectMem() throws ExecutionException {
        MemRawSample ret = new MemRawSample();
        try {
            Process p = new ProcessBuilder("sysctl", "vm.stats.vm.v_page_size", "vm.stats.vm.v_active_count", "vm.stats.vm.v_wire_count", "vm.stats.vm.v_cache_count", "kstat.zfs.misc.arcstats.size").redirectErrorStream(true).start();
            p.waitFor();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                long pageSize = 0, active = 0, wired = 0, cache = 0, arc = 0;
                // values from sysctl are in pages, usually 4096 bytes each, but we store in bytes
                // sysctl should always return a number of rows equal to the number of values requested, even in case of unknown oid
                if ((line = br.readLine()) != null && line.startsWith("vm.stats.vm.v_page_size")) {
                    pageSize = Long.parseLong(line.split("\\s+")[1]);
                }
                if ((line = br.readLine()) != null && line.startsWith("vm.stats.vm.v_active_count")) {
                    active = Long.parseLong(line.split("\\s+")[1]);
                }
                if ((line = br.readLine()) != null && line.startsWith("vm.stats.vm.v_wire_count")) {
                    wired = Long.parseLong(line.split("\\s+")[1]);
                }
                if ((line = br.readLine()) != null && line.startsWith("vm.stats.vm.v_cache_count")) {
                    cache = Long.parseLong(line.split("\\s+")[1]);
                }
                // value for arc is in raw bytes, ZFS module could be not loaded
                if ((line = br.readLine()) != null && line.startsWith("kstat.zfs.misc.arcstats.size") && !line.contains("unknown oid")) {
                    arc = Long.parseLong(line.split("\\s+")[1]);
                }
                ret.setMemUsed(active * pageSize + wired * pageSize - arc);
                ret.setCacheUsed(cache * pageSize + arc);
            }
        } catch (IOException | InterruptedException ex) {
            throw new ExecutionException("Unexpected error while running sysctl", ex);
        }
        try {
            Process p = new ProcessBuilder("sh", "-c", "swapinfo -k | grep -vi device | grep -vi total | cut -wf3").redirectErrorStream(true).start();
            p.waitFor();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                long swap = 0;
                // value is in kibibytes, but we store in bytes
                while ((line = br.readLine()) != null) {
                    swap += Long.parseLong(line) * 1024L;
                }
                ret.setSwapUsed(swap);
            }
        } catch (IOException | InterruptedException ex) {
            throw new ExecutionException("Unexpected error while running swapinfo", ex);
        }
        return ret;
    }

    @Override
    public NetRawSample collectNet(String device) throws ExecutionException {
        NetRawSample ret = new NetRawSample();
        ret.setDevice(device);
        try {
            Process p = new ProcessBuilder("sh", "-c", "netstat -b -n -I " + device + " | grep Link").redirectErrorStream(true).start();
            p.waitFor();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                if ((line = br.readLine()) != null) {
                    String[] split = line.split("\\s+");
                    ret.setRxBytes(Long.parseLong(split[7]));
                    ret.setTxBytes(Long.parseLong(split[10]));
                }
            }
        } catch (IOException | InterruptedException ex) {
            throw new ExecutionException("Unexpected error while running netstat", ex);
        }
        return ret;
    }

    @Override
    public DiskRawSample collectDisk(String device) throws ExecutionException {
        DiskRawSample ret = new DiskRawSample();
        ret.setDevice(device);
        String[] devList = device.split("\\+");
        long readBytes = 0, writeBytes = 0;
        try {
            Process p = new ProcessBuilder("sh", "-c", "iostat -Ixd " + device.replaceAll("\\+", " ")).redirectErrorStream(true).start();
            p.waitFor();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] split = line.split("\\s+");
                    for (String dev : devList) {
                        if (!isEmpty(dev) && split[0].equals(dev.trim())) {
                            // values in kibibytes
                            readBytes += (new BigDecimal(split[3]).multiply(new BigDecimal(1024)).setScale(0, RoundingMode.HALF_UP)).longValue();
                            writeBytes += (new BigDecimal(split[4]).multiply(new BigDecimal(1024)).setScale(0, RoundingMode.HALF_UP)).longValue();
                        }
                    }
                }
            }
        } catch (IOException | InterruptedException ex) {
            throw new ExecutionException("Unexpected error while running iostat", ex);
        }
        ret.setReadBytes(readBytes);
        ret.setWriteBytes(writeBytes);
        return ret;
    }

    @Override
    public DiskRawSample collectZFS(String device) throws ExecutionException {
        DiskRawSample ret = new DiskRawSample();
        ret.setDevice(device);
        long readBytes = 0, writeBytes = 0;
        try {
            Process p = new ProcessBuilder("sysctl", "kstat.zfs." + device + ".dataset").redirectErrorStream(true).start();
            p.waitFor();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("unknown oid")) {
                        return ret;
                    } else if (line.contains(".nread:")) {
                        readBytes += Long.parseLong(line.split("\\s", 2)[1].trim());
                    } else if (line.contains(".nwritten:")) {
                        writeBytes += Long.parseLong(line.split("\\s", 2)[1].trim());
                    }
                }
            }
        } catch (IOException | InterruptedException ex) {
            throw new ExecutionException("Unexpected error while running sysctl", ex);
        }
        ret.setReadBytes(readBytes);
        ret.setWriteBytes(writeBytes);
        return ret;
    }

    @Override
    public GpuRawSample collectGpu() throws ExecutionException {
        throw new ExecutionException("GPU probe type not supported on this platform");
    }

}
