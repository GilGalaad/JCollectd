package engine.collect;

import static engine.CommonUtils.isEmpty;
import engine.sample.CpuRawSample;
import engine.sample.DiskRawSample;
import engine.sample.LoadRawSample;
import engine.sample.MemRawSample;
import engine.sample.NetRawSample;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class FreeBSDCollectStrategy extends CollectStrategy {

    @Override
    public LoadRawSample collectLoadAvg() {
        LoadRawSample load = new LoadRawSample();
        try {
            Process p = new ProcessBuilder("sysctl", "vm.loadavg").redirectErrorStream(true).start();
            p.waitFor();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String[] split = br.readLine().split("\\s+");
                load.setLoad1minute(new BigDecimal(split[2]));
                load.setLoad5minute(new BigDecimal(split[3]));
                load.setLoad15minute(new BigDecimal(split[4]));
            }
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(String.format("Unexpected %s while reading sysctl, aborting - %s", ex.getClass().getSimpleName(), ex.getMessage()), ex);
        }
        return load;
    }

    @Override
    public CpuRawSample collectCpu() {
        CpuRawSample cpu = new CpuRawSample();
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
                cpu.setTotalTime(total);
                cpu.setIdleTime(Long.parseLong(split[4]) + Long.parseLong(split[5]));
            }
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(String.format("Unexpected %s while reading sysctl, aborting - %s", ex.getClass().getSimpleName(), ex.getMessage()), ex);
        }
        return cpu;
    }

    @Override
    public MemRawSample collectMem() {
        MemRawSample ret = new MemRawSample();
        try {
            Process p = new ProcessBuilder("sysctl", "vm.stats.vm.v_page_size", "vm.stats.vm.v_active_count", "vm.stats.vm.v_wire_count", "kstat.zfs.misc.arcstats.size").redirectErrorStream(true).start();
            p.waitFor();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                long pageSize = 0, active = 0, wired = 0, arc = 0;
                // values from sysctl are in pages, usually 4096 bytes each, but we store in bytes
                // sysctl sould always return a number or rows equal to the number of values requested, even in case of unknown oid
                if ((line = br.readLine()) != null && line.startsWith("vm.stats.vm.v_page_size")) {
                    pageSize = Long.parseLong(line.split("\\s+")[1]);
                }
                if ((line = br.readLine()) != null && line.startsWith("vm.stats.vm.v_active_count")) {
                    active = Long.parseLong(line.split("\\s+")[1]);
                }
                if ((line = br.readLine()) != null && line.startsWith("vm.stats.vm.v_wire_count")) {
                    wired = Long.parseLong(line.split("\\s+")[1]);
                }
                // value for arc is in raw bytes, ZFS module could be not loaded
                if ((line = br.readLine()) != null && line.startsWith("kstat.zfs.misc.arcstats.size") && !line.contains("unknown oid")) {
                    arc = Long.parseLong(line.split("\\s+")[1]);
                }
                ret.setMemUsed(active * pageSize + wired * pageSize - arc);
            }
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(String.format("Unexpected %s while reading sysctl, aborting - %s", ex.getClass().getSimpleName(), ex.getMessage()), ex);
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
            throw new RuntimeException(String.format("Unexpected %s while executing swapinfo, aborting - %s", ex.getClass().getSimpleName(), ex.getMessage()), ex);
        }
        return ret;
    }

    @Override
    public NetRawSample collectNet(String interfaceName) {
        NetRawSample ret = new NetRawSample();
        ret.setInterfaceName(interfaceName);
        try {
            Process p = new ProcessBuilder("sh", "-c", "netstat -b -n -I " + interfaceName + " | grep Link").redirectErrorStream(true).start();
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
            throw new RuntimeException(String.format("Unexpected %s while executing 'swapinfo', aborting - %s", ex.getClass().getSimpleName(), ex.getMessage()), ex);
        }
        return ret;
    }

    @Override
    public DiskRawSample collectDisk(String deviceName) {
        DiskRawSample ret = new DiskRawSample();
        ret.setDeviceName(deviceName);
        String[] devList = deviceName.split("\\+");
        long readBytes = 0, writeBytes = 0;
        try {
            Process p = new ProcessBuilder("sh", "-c", "iostat -Ixd " + deviceName.replaceAll("\\+", " ")).redirectErrorStream(true).start();
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
            throw new RuntimeException(String.format("Unexpected %s while executing 'swapinfo', aborting - %s", ex.getClass().getSimpleName(), ex.getMessage()), ex);
        }
        ret.setReadBytes(readBytes);
        ret.setWriteBytes(writeBytes);
        return ret;
    }

}
