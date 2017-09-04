package engine;

import static engine.JdbcUtils.ANALYZE;
import static engine.JdbcUtils.BEGIN_TRANS;
import static engine.JdbcUtils.CREATE_IDX_STMT;
import static engine.JdbcUtils.CREATE_TB_STMT;
import static engine.JdbcUtils.DEL_STMT;
import static engine.JdbcUtils.END_TRANS;
import static engine.JdbcUtils.INS_STMT;
import static engine.JdbcUtils.PRAGMA;
import static engine.JdbcUtils.SELECT_CPU;
import static engine.JdbcUtils.SELECT_HDD;
import static engine.JdbcUtils.SELECT_LOAD;
import static engine.JdbcUtils.SELECT_MEM;
import static engine.JdbcUtils.SELECT_NET;
import static engine.JdbcUtils.VACUUM;
import engine.config.CollectConfig;
import engine.config.ProbeConfig;
import engine.config.ProbeConfig.ProbeType;
import engine.samples.CollectResult;
import engine.samples.CpuSample;
import engine.samples.HddSample;
import engine.samples.LoadSample;
import engine.samples.MemSample;
import engine.samples.NetSample;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static main.JCollectd.isEmpty;
import static main.JCollectd.logger;
import static main.JCollectd.prettyPrint;

public class CollectEngine {

    private final CollectConfig conf;
    private final String connectionString;
    private final long samplingInterval;

    // results
    private CollectResult prevResult;
    private CollectResult curResult;

    // dates
    private final SimpleDateFormat sdfSql = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
    private final SimpleDateFormat sdfHr = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    public CollectEngine(CollectConfig conf) {
        this.conf = conf;
        connectionString = "jdbc:sqlite:" + conf.getDbPath().toString();
        samplingInterval = conf.getInterval() * 1000L;
    }

    public void run() {
        logger.log(INFO, "Entering in main loop");
        while (true) {
            // waiting for next schedule
            try {
                Thread.sleep(samplingInterval - (System.currentTimeMillis() % samplingInterval));
            } catch (InterruptedException ex) {
                return;
            }

            // making room for new samples
            prevResult = curResult;
            curResult = new CollectResult(conf.getProbeConfigList().size());
            long startCollectTime = 0, endCollectTime = 0, startSaveTime = 0, endSaveTime = 0, startReportTime = 0, endReportTime = 0, startCleanTime = 0, endCleanTime = 0;

            // collecting samples
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            startCollectTime = System.nanoTime();
            for (int i = 0; i < conf.getProbeConfigList().size(); i++) {
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
                logger.log(FINER, curResult.getProbeSampleList().get(i).toString());
            }
            endCollectTime = System.nanoTime();
            logger.log(FINE, "Collecting time: {0} msec", prettyPrint((endCollectTime - startCollectTime) / 1000000L));

            // saving results
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (prevResult != null) {
                startSaveTime = System.nanoTime();
                try (Connection conn = DriverManager.getConnection(connectionString)) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate(PRAGMA);
                        stmt.executeUpdate(CREATE_TB_STMT);
                        stmt.executeUpdate(CREATE_IDX_STMT);
                    }
                    try (Statement stmt = conn.createStatement(); PreparedStatement pstmt = conn.prepareStatement(INS_STMT)) {
                        stmt.executeUpdate(BEGIN_TRANS);
                        String sample_tms = sdfSql.format(curResult.getCollectTms());
                        long elapsedMsec = curResult.getCollectTms().getTime() - prevResult.getCollectTms().getTime();
                        for (int i = 0; i < conf.getProbeConfigList().size(); i++) {
                            if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.LOAD) {
                                saveLoadAvg(pstmt, i, sample_tms);
                            } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.CPU) {
                                saveCpu(pstmt, i, sample_tms);
                            } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.MEM) {
                                saveMem(pstmt, i, sample_tms);
                            } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.NET) {
                                saveNet(pstmt, i, sample_tms, elapsedMsec);
                            } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.HDD) {
                                saveHdd(pstmt, i, sample_tms, elapsedMsec);
                            }
                        }
                        pstmt.executeBatch();
                        stmt.executeUpdate(END_TRANS);
                    }
                } catch (SQLException ex) {
                    logger.log(SEVERE, "Error while saving samples to DB, aborting - {0}", ex.getMessage());
                    return;
                }
                endSaveTime = System.nanoTime();
                logger.log(FINE, "Saving time: {0} msec", prettyPrint((endSaveTime - startSaveTime) / 1000000L));
            }

            // generating report
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (prevResult != null) {
                startReportTime = System.nanoTime();
                try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(conf.getWebPath().toString()), Charset.forName("UTF-8"), new OpenOption[]{WRITE, CREATE, TRUNCATE_EXISTING});
                        Connection conn = DriverManager.getConnection(connectionString)) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate(PRAGMA);
                    }

                    // creating html from template
                    String report = readTemplate();
                    report = report.replace("XXX_TITLE_XXX", conf.getHostname());
                    report = report.replace("XXX_HOSTNAME_XXX", conf.getHostname());
                    report = report.replace("XXX_DATE_XXX", sdfHr.format(curResult.getCollectTms()));

                    // calculate reporting window
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(curResult.getCollectTms());
                    cal.add(Calendar.HOUR_OF_DAY, -conf.getReportHours());
                    String fromTime = sdfSql.format(cal.getTime());

                    // samples
                    StringBuilder jsDataSb = new StringBuilder();
                    StringBuilder bodySb = new StringBuilder();
                    for (int i = 0; i < conf.getProbeConfigList().size(); i++) {
                        if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.LOAD) {
                            jsDataSb.append(writeLoadJsData(conn, fromTime));
                            bodySb.append("<div id=\"div_load\" class=\"chart-container ");
                            bodySb.append(conf.getProbeConfigList().get(i).getGsize() == ProbeConfig.GraphSize.FULL_SIZE ? "full-size" : "half-size");
                            bodySb.append("\"></div>").append(System.lineSeparator());
                        } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.CPU) {
                            jsDataSb.append(writeCpuJsData(conn, fromTime));
                            bodySb.append("<div id=\"div_cpu\" class=\"chart-container ");
                            bodySb.append(conf.getProbeConfigList().get(i).getGsize() == ProbeConfig.GraphSize.FULL_SIZE ? "full-size" : "half-size");
                            bodySb.append("\"></div>").append(System.lineSeparator());
                        } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.MEM) {
                            jsDataSb.append(writeMemJsData(conn, fromTime));
                            bodySb.append("<div id=\"div_mem\" class=\"chart-container ");
                            bodySb.append(conf.getProbeConfigList().get(i).getGsize() == ProbeConfig.GraphSize.FULL_SIZE ? "full-size" : "half-size");
                            bodySb.append("\"></div>").append(System.lineSeparator());
                        } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.NET) {
                            jsDataSb.append(writeNetJsData(conn, fromTime, conf.getProbeConfigList().get(i).getDeviceName()));
                            bodySb.append("<div id=\"div_net_").append(conf.getProbeConfigList().get(i).getDeviceName()).append("\" class=\"chart-container ");
                            bodySb.append(conf.getProbeConfigList().get(i).getGsize() == ProbeConfig.GraphSize.FULL_SIZE ? "full-size" : "half-size");
                            bodySb.append("\"></div>").append(System.lineSeparator());
                        } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.HDD) {
                            jsDataSb.append(writeHddJsData(conn, fromTime, conf.getProbeConfigList().get(i).getDeviceName()));
                            bodySb.append("<div id=\"div_hdd_").append(conf.getProbeConfigList().get(i).getDeviceName()).append("\" class=\"chart-container ");
                            bodySb.append(conf.getProbeConfigList().get(i).getGsize() == ProbeConfig.GraphSize.FULL_SIZE ? "full-size" : "half-size");
                            bodySb.append("\"></div>").append(System.lineSeparator());
                        }
                    }

                    // replacing placeholders
                    report = report.replace("XXX_JSDATA_XXX\n", jsDataSb.toString());
                    report = report.replace("XXX_BODY_XXX\n", bodySb.toString());
                    report = report.replace("XXX_TIMINGS_XXX",
                            String.format("Time spent collecting samples: %,dms, writing samples: %,dms, generating report: %,dms",
                                    (endCollectTime - startCollectTime) / 1000000L,
                                    (endSaveTime - startSaveTime) / 1000000L,
                                    (System.nanoTime() - startReportTime) / 1000000L));

                    // writing to file
                    bw.write(report);
                } catch (IOException ex) {
                    logger.log(SEVERE, "I/O error while generating HTML report, aborting - {0}", ex.getMessage());
                    return;
                } catch (SQLException ex) {
                    logger.log(SEVERE, "Error while reading samples from DB, aborting - {0}", ex.getMessage());
                    return;
                }
                endReportTime = System.nanoTime();
                logger.log(FINE, "Reporting time: {0} msec", prettyPrint((endReportTime - startReportTime) / 1000000L));
            }

            // janitor work, once a hour
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (prevResult != null) {
                // calculate reporting window
                Calendar cal = Calendar.getInstance();
                cal.setTime(curResult.getCollectTms());
                if (cal.get(Calendar.MINUTE) == 0 && cal.get(Calendar.SECOND) == 0) {
                    cal.add(Calendar.HOUR_OF_DAY, -conf.getRetentionHours());
                    String fromTime = sdfSql.format(cal.getTime());
                    startCleanTime = System.nanoTime();
                    try (Connection conn = DriverManager.getConnection(connectionString)) {
                        try (Statement stmt = conn.createStatement()) {
                            stmt.executeUpdate(PRAGMA);
                        }
                        try (Statement stmt = conn.createStatement(); PreparedStatement pstmt = conn.prepareStatement(DEL_STMT)) {
                            stmt.executeUpdate(BEGIN_TRANS);
                            pstmt.setString(1, fromTime);
                            pstmt.executeUpdate();
                            stmt.executeUpdate(END_TRANS);
                        }
                        try (Statement stmt = conn.createStatement()) {
                            stmt.executeUpdate(ANALYZE);
                        }
                        try (Statement stmt = conn.createStatement()) {
                            stmt.executeUpdate(VACUUM);
                        }
                    } catch (SQLException ex) {
                        logger.log(SEVERE, "Error while cleaning up DB, aborting - {0}", ex.getMessage());
                        return;
                    }
                    endCleanTime = System.nanoTime();
                    logger.log(FINE, "Cleanup time: {0} msec", prettyPrint((endCleanTime - startCleanTime) / 1000000L));
                }
            }
        }
    }

    private String readTemplate() throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/template.html"), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
            return sb.toString();
        }
    }

    /*
     * methods for parsing data from Operating System
     */
    private LoadSample parseLoadAvg() {
        LoadSample load = new LoadSample();
        if (System.getProperty("os.name").equals("Linux")) {
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/loadavg"))) {
                String[] split = br.readLine().split("\\s+");
                load.setLoad1minute(new BigDecimal(split[0]));
                load.setLoad5minute(new BigDecimal(split[1]));
                load.setLoad15minute(new BigDecimal(split[2]));
            } catch (IOException ex) {
                // can't happen
            }
        } else if (System.getProperty("os.name").equals("FreeBSD")) {
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
                String[] split = br.readLine().split("\\s+");
                long total = 0;
                for (int i = 1; i < split.length; i++) {
                    total += Long.parseLong(split[i]);
                }
                cpu.setTotalTime(total);
                cpu.setIdleTime(Long.parseLong(split[4]) + Long.parseLong(split[5]));
            } catch (IOException ex) {
                // can't happen
            }
        } else if (System.getProperty("os.name").equals("FreeBSD")) {
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
                // can't happen
            }
        } else if (System.getProperty("os.name").equals("FreeBSD")) {
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
                p = new ProcessBuilder("sh", "-c", "swapinfo -k | grep -i total | cut -wf3").redirectErrorStream(true).start();
                p.waitFor();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    // value is in kibibytes, but we store in bytes
                    if ((line = br.readLine()) != null) {
                        ret.setSwapUsed(Long.parseLong(line) * 1024L);
                    }
                }
            } catch (IOException | InterruptedException ex) {
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
                    break;
                }
            } catch (IOException ex) {
                // can't happen
            }
        } else if (System.getProperty("os.name").equals("FreeBSD")) {
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
                // can't happen
            }
        }
        return ret;
    }

    private HddSample parseDisk(String deviceName) {
        HddSample ret = new HddSample();
        ret.setDeviceName(deviceName);
        String[] devList = deviceName.split("_");
        long readBytes = 0, writeBytes = 0;
        if (System.getProperty("os.name").equals("Linux")) {
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
                // can't happen
            }
        } else if (System.getProperty("os.name").equals("FreeBSD")) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "iostat -Ixd " + deviceName.replaceAll("_", " ")).redirectErrorStream(true).start();
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
                // can't happen
            }
        }
        ret.setReadBytes(readBytes);
        ret.setWriteBytes(writeBytes);
        return ret;
    }

    /*
     * methods for calculating values from raw samples, if necessary, and saving them into database
     */
    private void saveLoadAvg(PreparedStatement pstmt, int i, String sample_tms) throws SQLException {
        LoadSample cs = (LoadSample) curResult.getProbeSampleList().get(i);
        pstmt.setString(1, "load1m");
        pstmt.setString(2, sample_tms);
        pstmt.setString(3, cs.getLoad1minute().toString());
        pstmt.addBatch();
        pstmt.setString(1, "load5m");
        pstmt.setString(2, sample_tms);
        pstmt.setString(3, cs.getLoad5minute().toString());
        pstmt.addBatch();
        pstmt.setString(1, "load15m");
        pstmt.setString(2, sample_tms);
        pstmt.setString(3, cs.getLoad15minute().toString());
        pstmt.addBatch();
    }

    private void saveCpu(PreparedStatement pstmt, int i, String sample_tms) throws SQLException {
        // cpu saved in percent, rounded to 1 significant digit
        CpuSample cs = (CpuSample) curResult.getProbeSampleList().get(i);
        CpuSample ps = (CpuSample) prevResult.getProbeSampleList().get(i);
        pstmt.setString(1, "cpu");
        pstmt.setString(2, sample_tms);
        long diffTotal = cs.getTotalTime() - ps.getTotalTime();
        long diffIdle = cs.getIdleTime() - ps.getIdleTime();
        BigDecimal cpu = new BigDecimal(100.0 * (diffTotal - diffIdle) / diffTotal).setScale(1, RoundingMode.HALF_UP);
        pstmt.setString(3, cpu.toString());
        pstmt.addBatch();
    }

    private void saveMem(PreparedStatement pstmt, int i, String sample_tms) throws SQLException {
        // mem saved in mebibyte, rounded to nearest integer
        MemSample cs = (MemSample) curResult.getProbeSampleList().get(i);
        pstmt.setString(1, "mem");
        pstmt.setString(2, sample_tms);
        BigDecimal mem = new BigDecimal(cs.getMemUsed() / 1024.0 / 1024.0).setScale(0, RoundingMode.HALF_UP);
        pstmt.setString(3, mem.toString());
        pstmt.addBatch();
        pstmt.setString(1, "swap");
        pstmt.setString(2, sample_tms);
        BigDecimal swap = new BigDecimal(cs.getSwapUsed() / 1024.0 / 1024.0).setScale(0, RoundingMode.HALF_UP);
        pstmt.setString(3, swap.toString());
        pstmt.addBatch();
    }

    private void saveNet(PreparedStatement pstmt, int i, String sample_tms, long elapsedMsec) throws SQLException {
        // net saved in kibibyte/s, rounded to nearest integer
        NetSample cs = (NetSample) curResult.getProbeSampleList().get(i);
        NetSample ps = (NetSample) prevResult.getProbeSampleList().get(i);
        pstmt.setString(1, "net_tx_" + cs.getInterfaceName());
        pstmt.setString(2, sample_tms);
        BigDecimal tx = new BigDecimal((cs.getTxBytes() - ps.getTxBytes()) / 1024.0 / elapsedMsec * 1000.0).setScale(0, RoundingMode.HALF_UP);
        pstmt.setString(3, tx.toString());
        pstmt.addBatch();
        pstmt.setString(1, "net_rx_" + cs.getInterfaceName());
        pstmt.setString(2, sample_tms);
        BigDecimal rx = new BigDecimal((cs.getRxBytes() - ps.getRxBytes()) / 1024.0 / elapsedMsec * 1000.0).setScale(0, RoundingMode.HALF_UP);
        pstmt.setString(3, rx.toString());
        pstmt.addBatch();
    }

    private void saveHdd(PreparedStatement pstmt, int i, String sample_tms, long elapsedMsec) throws SQLException {
        // net saved in mbyte/s, rounded to 1 significant digit
        HddSample cs = (HddSample) curResult.getProbeSampleList().get(i);
        HddSample ps = (HddSample) prevResult.getProbeSampleList().get(i);
        pstmt.setString(1, "hdd_read_" + cs.getDeviceName());
        pstmt.setString(2, sample_tms);
        BigDecimal read = new BigDecimal((cs.getReadBytes() - ps.getReadBytes()) / 1024.0 / 1024.0 / elapsedMsec * 1000.0).setScale(1, RoundingMode.HALF_UP);
        pstmt.setString(3, read.toString());
        pstmt.addBatch();
        pstmt.setString(1, "hdd_write_" + cs.getDeviceName());
        pstmt.setString(2, sample_tms);
        BigDecimal write = new BigDecimal((cs.getWriteBytes() - ps.getWriteBytes()) / 1024.0 / 1024.0 / elapsedMsec * 1000.0).setScale(1, RoundingMode.HALF_UP);
        pstmt.setString(3, write.toString());
        pstmt.addBatch();
    }

    /*
     * methods for loading data from database, and writing it into javascript array
     */
    private String writeLoadJsData(Connection conn, String fromTime) throws SQLException, IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("google.charts.setOnLoadCallback(drawLoad);").append(System.lineSeparator());
        sb.append("function drawLoad() {").append(System.lineSeparator());
        sb.append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        sb.append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append("data.addColumn('number', '1 min');").append(System.lineSeparator());
        sb.append("data.addColumn('number', '5 min');").append(System.lineSeparator());
        sb.append("data.addColumn('number', '15 min');").append(System.lineSeparator());

        try (PreparedStatement stmt = conn.prepareStatement(SELECT_LOAD)) {
            stmt.setString(1, fromTime);
            try (ResultSet rs = stmt.executeQuery()) {
                Calendar cal = Calendar.getInstance();
                while (rs.next()) {
                    try {
                        cal.setTime(sdfSql.parse(rs.getString(1)));
                    } catch (ParseException ex) {
                        // can't happen, but just in case we skip the line
                        continue;
                    }
                    sb.append("data.addRows([[new Date(");
                    sb.append(cal.get(Calendar.YEAR)).append(",");
                    sb.append(cal.get(Calendar.MONTH)).append(",");
                    sb.append(cal.get(Calendar.DAY_OF_MONTH)).append(",");
                    sb.append(cal.get(Calendar.HOUR_OF_DAY)).append(",");
                    sb.append(cal.get(Calendar.MINUTE)).append(",");
                    sb.append(cal.get(Calendar.SECOND)).append("),");
                    sb.append(rs.getString(2)).append(",");
                    sb.append(rs.getString(3)).append(",");
                    sb.append(rs.getString(4));
                    sb.append("]]);");
                    sb.append(System.lineSeparator());
                }
            }
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/options_load.js"), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }

        sb.append("var chart = new google.visualization.AreaChart(document.getElementById('div_load'));").append(System.lineSeparator());
        sb.append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString();
    }

    private String writeCpuJsData(Connection conn, String fromTime) throws SQLException, IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("google.charts.setOnLoadCallback(drawCpu);").append(System.lineSeparator());
        sb.append("function drawCpu() {").append(System.lineSeparator());
        sb.append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        sb.append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'CPU');").append(System.lineSeparator());

        try (PreparedStatement stmt = conn.prepareStatement(SELECT_CPU)) {
            stmt.setString(1, fromTime);
            try (ResultSet rs = stmt.executeQuery()) {
                Calendar cal = Calendar.getInstance();
                while (rs.next()) {
                    try {
                        cal.setTime(sdfSql.parse(rs.getString(1)));
                    } catch (ParseException ex) {
                        // can't happen, but just in case we skip the line
                        continue;
                    }
                    sb.append("data.addRows([[new Date(");
                    sb.append(cal.get(Calendar.YEAR)).append(",");
                    sb.append(cal.get(Calendar.MONTH)).append(",");
                    sb.append(cal.get(Calendar.DAY_OF_MONTH)).append(",");
                    sb.append(cal.get(Calendar.HOUR_OF_DAY)).append(",");
                    sb.append(cal.get(Calendar.MINUTE)).append(",");
                    sb.append(cal.get(Calendar.SECOND)).append("),");
                    sb.append(rs.getString(2));
                    sb.append("]]);");
                    sb.append(System.lineSeparator());
                }
            }
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/options_cpu.js"), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }

        sb.append("var chart = new google.visualization.AreaChart(document.getElementById('div_cpu'));").append(System.lineSeparator());
        sb.append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString();
    }

    private String writeMemJsData(Connection conn, String fromTime) throws SQLException, IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("google.charts.setOnLoadCallback(drawMem);").append(System.lineSeparator());
        sb.append("function drawMem() {").append(System.lineSeparator());
        sb.append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        sb.append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'Physical memory');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'Swap');").append(System.lineSeparator());

        try (PreparedStatement stmt = conn.prepareStatement(SELECT_MEM)) {
            stmt.setString(1, fromTime);
            try (ResultSet rs = stmt.executeQuery()) {
                Calendar cal = Calendar.getInstance();
                while (rs.next()) {
                    try {
                        cal.setTime(sdfSql.parse(rs.getString(1)));
                    } catch (ParseException ex) {
                        // can't happen, but just in case we skip the line
                        continue;
                    }
                    sb.append("data.addRows([[new Date(");
                    sb.append(cal.get(Calendar.YEAR)).append(",");
                    sb.append(cal.get(Calendar.MONTH)).append(",");
                    sb.append(cal.get(Calendar.DAY_OF_MONTH)).append(",");
                    sb.append(cal.get(Calendar.HOUR_OF_DAY)).append(",");
                    sb.append(cal.get(Calendar.MINUTE)).append(",");
                    sb.append(cal.get(Calendar.SECOND)).append("),");
                    sb.append(rs.getString(2)).append(",");
                    sb.append(rs.getString(3));
                    sb.append("]]);");
                    sb.append(System.lineSeparator());
                }
            }
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/options_mem.js"), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }

        sb.append("var chart = new google.visualization.AreaChart(document.getElementById('div_mem'));").append(System.lineSeparator());
        sb.append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString();
    }

    private String writeNetJsData(Connection conn, String fromTime, String interfaceName) throws SQLException, IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("google.charts.setOnLoadCallback(drawNet_").append(interfaceName).append(");").append(System.lineSeparator());
        sb.append("function drawNet_").append(interfaceName).append("() {").append(System.lineSeparator());
        sb.append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        sb.append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'TX');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'RX');").append(System.lineSeparator());

        try (PreparedStatement stmt = conn.prepareStatement(SELECT_NET)) {
            stmt.setString(1, fromTime);
            stmt.setString(2, "net_tx_" + interfaceName);
            stmt.setString(3, "net_rx_" + interfaceName);
            try (ResultSet rs = stmt.executeQuery()) {
                Calendar cal = Calendar.getInstance();
                while (rs.next()) {
                    try {
                        cal.setTime(sdfSql.parse(rs.getString(1)));
                    } catch (ParseException ex) {
                        // can't happen, but just in case we skip the line
                        continue;
                    }
                    sb.append("data.addRows([[new Date(");
                    sb.append(cal.get(Calendar.YEAR)).append(",");
                    sb.append(cal.get(Calendar.MONTH)).append(",");
                    sb.append(cal.get(Calendar.DAY_OF_MONTH)).append(",");
                    sb.append(cal.get(Calendar.HOUR_OF_DAY)).append(",");
                    sb.append(cal.get(Calendar.MINUTE)).append(",");
                    sb.append(cal.get(Calendar.SECOND)).append("),");
                    sb.append(rs.getString(2)).append(",");
                    sb.append("-").append(rs.getString(3));
                    sb.append("]]);");
                    sb.append(System.lineSeparator());
                }
            }
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/options_net.js"), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }

        sb.append("var chart = new google.visualization.AreaChart(document.getElementById('div_net_").append(interfaceName).append("'));").append(System.lineSeparator());
        sb.append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString().replace("REPLACEME", interfaceName);
    }

    private String writeHddJsData(Connection conn, String fromTime, String deviceName) throws SQLException, IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("google.charts.setOnLoadCallback(drawHdd_").append(deviceName).append(");").append(System.lineSeparator());
        sb.append("function drawHdd_").append(deviceName).append("() {").append(System.lineSeparator());
        sb.append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        sb.append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'Read');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'Write');").append(System.lineSeparator());

        try (PreparedStatement stmt = conn.prepareStatement(SELECT_HDD)) {
            stmt.setString(1, fromTime);
            stmt.setString(2, "hdd_read_" + deviceName);
            stmt.setString(3, "hdd_write_" + deviceName);
            try (ResultSet rs = stmt.executeQuery()) {
                Calendar cal = Calendar.getInstance();
                while (rs.next()) {
                    try {
                        cal.setTime(sdfSql.parse(rs.getString(1)));
                    } catch (ParseException ex) {
                        // can't happen, but just in case we skip the line
                        continue;
                    }
                    sb.append("data.addRows([[new Date(");
                    sb.append(cal.get(Calendar.YEAR)).append(",");
                    sb.append(cal.get(Calendar.MONTH)).append(",");
                    sb.append(cal.get(Calendar.DAY_OF_MONTH)).append(",");
                    sb.append(cal.get(Calendar.HOUR_OF_DAY)).append(",");
                    sb.append(cal.get(Calendar.MINUTE)).append(",");
                    sb.append(cal.get(Calendar.SECOND)).append("),");
                    sb.append(rs.getString(2)).append(",");
                    sb.append("-").append(rs.getString(3));
                    sb.append("]]);");
                    sb.append(System.lineSeparator());
                }
            }
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/options_hdd.js"), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }

        sb.append("var chart = new google.visualization.AreaChart(document.getElementById('div_hdd_").append(deviceName).append("'));").append(System.lineSeparator());
        sb.append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString().replace("REPLACEME", deviceName);
    }

}
