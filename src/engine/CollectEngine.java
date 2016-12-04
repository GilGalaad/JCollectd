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
import static main.JCollectd.logger;
import static main.JCollectd.prettyPrint;

public class CollectEngine {

    private final CollectConfig conf;
    private final long SAMPLING_INTERVAL = 5000L;

    // results
    private CollectResult prevResult;
    private CollectResult curResult;

    // dates
    private final SimpleDateFormat sdfms = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    // jdbc
    private static final String CREATE_TB_STMT = "CREATE TABLE IF NOT EXISTS probe_samples (id_sample INTEGER PRIMARY KEY, probe_name TEXT, sample_tms TEXT, sample_value TEXT)";
    private static final String CREATE_IDX_STMT = "CREATE UNIQUE INDEX IF NOT EXISTS idx_probe_samples ON probe_samples (probe_name, sample_tms)";
    private static final String INS_STMT = "INSERT INTO probe_samples (probe_name, sample_tms, sample_value) VALUES (?,?,?)";
    private static final String BEGIN_TRANS = "BEGIN TRANSACTION";
    private static final String END_TRANS = "END TRANSACTION";
    private static final String PRAGMA = "PRAGMA busy_timeout = 5000";
    private static final String SELECT_LOAD = "SELECT sample_tms,\n"
            + "MAX(CASE WHEN probe_name = 'load1m' THEN sample_value ELSE NULL END) l1m,\n"
            + "MAX(CASE WHEN probe_name = 'load5m' THEN sample_value ELSE NULL END) l5m,\n"
            + "MAX(CASE WHEN probe_name = 'load15m' THEN sample_value ELSE NULL END) l15m\n"
            + "FROM probe_samples\n"
            + "WHERE sample_tms > ?\n"
            + "AND (probe_name = 'load1m' OR probe_name = 'load5m' OR probe_name = 'load15m')\n"
            + "GROUP BY sample_tms ORDER BY sample_tms;";

    public CollectEngine(CollectConfig conf) {
        this.conf = conf;
    }

    public void run() {
        logger.info("Entering in main loop");
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
            long startCollectTime, endCollectTime, startSaveTime, endSaveTime, startReportTime, endReportTime;

            // collecting samples
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Received KILL signal, exiting from main loop");
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
                logger.debug(curResult.getProbeSampleList().get(i).toString());
            }
            endCollectTime = System.nanoTime();
            logger.info("Collecting time: {} msec", prettyPrint((endCollectTime - startCollectTime) / 1000000L));

            // saving results
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Received KILL signal, exiting from main loop");
                return;
            }
            if (prevResult != null) {
                startSaveTime = System.nanoTime();
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + conf.getDbPath().toString())) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate(PRAGMA);
                        stmt.executeUpdate(CREATE_TB_STMT);
                        stmt.executeUpdate(CREATE_IDX_STMT);
                    }
                    try (Statement stmt = conn.createStatement(); PreparedStatement pstmt = conn.prepareStatement(INS_STMT)) {
                        stmt.executeUpdate(BEGIN_TRANS);
                        String sample_tms = sdfms.format(curResult.getCollectTms());
                        long elapsedMsec = curResult.getCollectTms().getTime() - prevResult.getCollectTms().getTime();
                        for (int i = 0; i < conf.getProbeConfigList().size(); i++) {
                            if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.LOAD) {
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
                            } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.CPU) {
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
                            } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.MEM) {
                                // mem saved in mebibyte, rounded to nearest integer
                                MemSample cs = (MemSample) curResult.getProbeSampleList().get(i);
                                pstmt.setString(1, "mem");
                                pstmt.setString(2, sample_tms);
                                BigDecimal mem = new BigDecimal(cs.getMemUsed() / 1024.0).setScale(0, RoundingMode.HALF_UP);
                                pstmt.setString(3, mem.toString());
                                pstmt.addBatch();
                                pstmt.setString(1, "swap");
                                pstmt.setString(2, sample_tms);
                                BigDecimal swap = new BigDecimal(cs.getSwapUsed() / 1024.0).setScale(0, RoundingMode.HALF_UP);
                                pstmt.setString(3, swap.toString());
                                pstmt.addBatch();
                            } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.NET) {
                                // net saved in kbyte/s, rounded to nearest integer
                                NetSample cs = (NetSample) curResult.getProbeSampleList().get(i);
                                NetSample ps = (NetSample) prevResult.getProbeSampleList().get(i);
                                pstmt.setString(1, "net_rx_" + cs.getInterfaceName());
                                pstmt.setString(2, sample_tms);
                                BigDecimal rx = new BigDecimal((cs.getRxBytes() - ps.getRxBytes()) / 1024.0 / elapsedMsec * 1000.0).setScale(0, RoundingMode.HALF_UP);
                                pstmt.setString(3, rx.toString());
                                pstmt.addBatch();
                                pstmt.setString(1, "net_tx_" + cs.getInterfaceName());
                                pstmt.setString(2, sample_tms);
                                BigDecimal tx = new BigDecimal((cs.getTxBytes() - ps.getTxBytes()) / 1024.0 / elapsedMsec * 1000.0).setScale(0, RoundingMode.HALF_UP);
                                pstmt.setString(3, tx.toString());
                                pstmt.addBatch();
                            } else if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.HDD) {
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
                        }
                        pstmt.executeBatch();
                        stmt.executeUpdate(END_TRANS);
                    }
                } catch (SQLException ex) {
                    logger.fatal("Error while saving samples to DB, aborting - {}", ex.getMessage());
                    return;
                }
                endSaveTime = System.nanoTime();
                logger.info("Saving time: {} msec", prettyPrint((endSaveTime - startSaveTime) / 1000000L));
            }

            // generating report
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Received KILL signal, exiting from main loop");
                return;
            }
            if (prevResult != null) {
                startReportTime = System.nanoTime();
                try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(conf.getWebPath().toString()), Charset.forName("UTF-8"), new OpenOption[]{WRITE, CREATE, TRUNCATE_EXISTING});
                        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + conf.getDbPath().toString())) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate(PRAGMA);
                    }
                    StringBuilder sb = new StringBuilder();
                    // header
                    sb.append(writeHeader(conf.getHostname()));

                    // calculate reporting window
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(curResult.getCollectTms());
                    cal.add(Calendar.HOUR_OF_DAY, -conf.getReportHours());
                    String fromTime = sdf.format(cal.getTime());

                    // samples
                    for (int i = 0; i < conf.getProbeConfigList().size(); i++) {
                        if (conf.getProbeConfigList().get(i).getPtype() == ProbeType.LOAD) {
                            sb.append(writeLoad(conn, fromTime));
                        }
                    }
                    sb.append("</script>").append(System.lineSeparator());
                    sb.append("</head>\n"
                            + "    <body>\n"
                            + "        <div class=\"navbar\">\n"
                            + "            <span class=\"navlenft\"><i class=\"fa fa-area-chart\"></i> DarkSun</span>\n"
                            + "            <span class=\"navright\"><i class=\"fa fa-clock-o\"></i> 04/12/2016 10:25:01</span>\n"
                            + "        </div>\n"
                            + "        <div class=\"main-container\">\n"
                            + "<div class=\"chart-container\" id=\"div_load\"></div>"
                            + "            <div class=\"footer\">\n"
                            + "                <p>Time spent collecting samples: 24ms, writing samples: 484ms, reading samples: 56ms, generating report: 11ms</p>\n"
                            + "            </div>\n"
                            + "        </div>\n"
                            + "    </body>\n"
                            + "</html>").append(System.lineSeparator());
                    String report = sb.toString();
                    bw.write(report);
                } catch (IOException ex) {
                    logger.fatal("I/O error while generating HTML report, aborting - {}", ex.getMessage());
                    return;
                } catch (SQLException ex) {
                    logger.fatal("Error while reading samples from DB, aborting - {}", ex.getMessage());
                    return;
                }
                endReportTime = System.nanoTime();
                logger.info("Reporting time: {} msec", prettyPrint((endReportTime - startReportTime) / 1000000L));
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

    private String writeHeader(String hostname) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/header.html"), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
            sb.append(System.lineSeparator());
            return sb.toString().replaceFirst("REPLACEME", hostname);
        }
    }

    private String writeLoad(Connection conn, String fromTime) throws SQLException, IOException {
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
                        cal.setTime(sdf.parse(rs.getString(1)));
                    } catch (ParseException ex) {
                        // can't happen, but just in case we skip the line
                        continue;
                    }
                    sb.append("data.addRows([[new Date(");
                    sb.append(cal.get(Calendar.YEAR)).append(",");
                    sb.append(cal.get(Calendar.MONTH) + 1).append(",");
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
        sb.append("}").append(System.lineSeparator());
        return sb.toString();
    }
}
