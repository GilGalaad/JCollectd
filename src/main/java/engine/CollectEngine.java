package engine;

import static common.CommonUtils.HOUR_MS;
import static common.CommonUtils.smartElapsed;
import common.exception.ExecutionException;
import static engine.ReportUtils.optsCpuJs;
import static engine.ReportUtils.optsDiskJs;
import static engine.ReportUtils.optsGpuJs;
import static engine.ReportUtils.optsLoadJs;
import static engine.ReportUtils.optsMemJs;
import static engine.ReportUtils.optsNetJs;
import static engine.ReportUtils.templateHtml;
import engine.collect.CollectStrategy;
import engine.collect.FreeBSDCollectStrategy;
import engine.collect.LinuxCollectStrategy;
import engine.config.CollectConfiguration;
import static engine.config.CollectConfiguration.DbEngine.SQLITE;
import static engine.config.CollectConfiguration.OperatingSystem.FREEBSD;
import static engine.config.CollectConfiguration.OperatingSystem.LINUX;
import engine.config.ProbeConfiguration;
import engine.config.ProbeConfiguration.ProbeType;
import engine.db.DatabaseStrategy;
import engine.db.model.TbProbeSeries;
import engine.db.sqlite.SqliteStrategy;
import engine.sample.CollectResult;
import engine.sample.CpuRawSample;
import engine.sample.DiskRawSample;
import engine.sample.GpuRawSample;
import engine.sample.LoadRawSample;
import engine.sample.MemRawSample;
import engine.sample.NetRawSample;
import java.io.BufferedWriter;
import java.io.IOException;
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
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CollectEngine {

    private final CollectConfiguration conf;
    private final long samplingInterval;
    private final CollectStrategy collectStrategy;
    private final DatabaseStrategy databaseStrategy;

    // results
    private CollectResult prevResult;
    private CollectResult curResult;

    // timings
    private long startCollectTime;
    private long endCollectTime;
    private long startPersistTime;
    private long endPersistTime;
    private long startReportTime;
    private long endReportTime;
    private long startCleanTime;
    private long endCleanTime;
    private Date lastMaintenance;

    // dates
    private final SimpleDateFormat sdfHtml = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    public CollectEngine(CollectConfiguration conf) {
        this.conf = conf;
        this.samplingInterval = conf.getInterval() * 1000L;
        lastMaintenance = new Date();

        if (conf.getOs() == FREEBSD) {
            collectStrategy = new FreeBSDCollectStrategy();
        } else if (conf.getOs() == LINUX) {
            collectStrategy = new LinuxCollectStrategy();
        } else {
            throw new UnsupportedOperationException(String.format("Unsupported Operating System: %s", conf.getOs()));
        }

        if (conf.getDbEngine() == SQLITE) {
            databaseStrategy = new SqliteStrategy(conf);
        } else {
            throw new UnsupportedOperationException(String.format("Unsupported Database engine: %s", conf.getDbEngine()));
        }
    }

    public void run() throws ExecutionException {
        log.info("Entering in main loop");
        while (true) {
            // waiting for next schedule
            try {
                Thread.sleep((samplingInterval - (System.currentTimeMillis() % samplingInterval)) % samplingInterval);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }

            // making room for new samples
            prevResult = curResult;
            curResult = new CollectResult(conf.getProbeConfigList().size());
            if (log.isDebugEnabled()) {
                log.debug("Worker thread wake up at {}", sdfHtml.format(curResult.getCollectTms()));
            }

            // collecting samples
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            startCollectTime = System.nanoTime();
            doCollect();
            endCollectTime = System.nanoTime();
            if (log.isDebugEnabled()) {
                log.debug("Collecting time: {}", smartElapsed(endCollectTime - startCollectTime));
            }

            // persisting timeseries
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (prevResult != null) {
                startPersistTime = System.nanoTime();
                doPersist();
                endPersistTime = System.nanoTime();
                if (log.isDebugEnabled()) {
                    log.debug("Persisting time: {}", smartElapsed(endPersistTime - startPersistTime));
                }
            }

            // generating report
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (conf.getWebPath() != null && prevResult != null) {
                startReportTime = System.nanoTime();
                doReport();
                endReportTime = System.nanoTime();
                if (log.isDebugEnabled()) {
                    log.debug("Reporting time: {}", smartElapsed(endReportTime - startReportTime));
                }
            }

            // janitor work, once a hour
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (prevResult != null && (curResult.getCollectTms().getTime() - lastMaintenance.getTime()) >= (HOUR_MS)) {
                lastMaintenance = curResult.getCollectTms();
                startCleanTime = System.nanoTime();
                doMaintenance();
                endCleanTime = System.nanoTime();
                if (log.isDebugEnabled()) {
                    log.debug("Cleanup time: {}", smartElapsed(endCleanTime - startCleanTime));
                }
            }
        }
    }

    // parsing data from Operating System
    private void doCollect() throws ExecutionException {
        for (int i = 0; i < conf.getProbeConfigList().size(); i++) {
            if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.LOAD) {
                curResult.getProbeRawSampleList().add(i, collectStrategy.collectLoadAvg());
            } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.CPU) {
                curResult.getProbeRawSampleList().add(i, collectStrategy.collectCpu());
            } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.MEM) {
                curResult.getProbeRawSampleList().add(i, collectStrategy.collectMem());
            } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.NET) {
                curResult.getProbeRawSampleList().add(i, collectStrategy.collectNet(conf.getProbeConfigList().get(i).getDevice()));
            } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.DISK) {
                curResult.getProbeRawSampleList().add(i, collectStrategy.collectDisk(conf.getProbeConfigList().get(i).getDevice()));
            } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.GPU) {
                curResult.getProbeRawSampleList().add(i, collectStrategy.collectGpu());
            } else {
                throw new UnsupportedOperationException(String.format("Unsupported probe type: %s", conf.getProbeConfigList().get(i).getPrType()));
            }
            if (log.isTraceEnabled()) {
                log.trace(curResult.getProbeRawSampleList().get(i).toString());
            }
        }
    }

    // persisting timeseries
    private void doPersist() throws ExecutionException {
        ArrayList<TbProbeSeries> tmsList = rawSamplesToTimeseries();
        try (Connection conn = databaseStrategy.getConnection()) {
            databaseStrategy.prepareSchema(conn);
            databaseStrategy.persistTimeseries(conn, tmsList);
        } catch (SQLException ex) {
            throw new ExecutionException("Error while persisting samples to DB", ex);
        }
    }

    // creating timeseries values from raw samples
    private ArrayList<TbProbeSeries> rawSamplesToTimeseries() {
        ArrayList<TbProbeSeries> series = new ArrayList<>();
        for (int i = 0; i < conf.getProbeConfigList().size(); i++) {
            if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.LOAD) {
                LoadRawSample cs = (LoadRawSample) curResult.getProbeRawSampleList().get(i);
                TbProbeSeries s1 = new TbProbeSeries();
                s1.setHostname(conf.getHostname());
                s1.setProbeType("load1m");
                s1.setDevice(null);
                s1.setSampleTms(curResult.getCollectTms());
                s1.setSampleValue(cs.getLoad1minute());
                series.add(s1);
                TbProbeSeries s2 = new TbProbeSeries();
                s2.setHostname(conf.getHostname());
                s2.setProbeType("load5m");
                s2.setDevice(null);
                s2.setSampleTms(curResult.getCollectTms());
                s2.setSampleValue(cs.getLoad5minute());
                series.add(s2);
                TbProbeSeries s3 = new TbProbeSeries();
                s3.setHostname(conf.getHostname());
                s3.setProbeType("load15m");
                s3.setDevice(null);
                s3.setSampleTms(curResult.getCollectTms());
                s3.setSampleValue(cs.getLoad15minute());
                series.add(s3);
            } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.CPU) {
                // cpu saved in percent, rounded to 1 significant digit
                CpuRawSample cs = (CpuRawSample) curResult.getProbeRawSampleList().get(i);
                CpuRawSample ps = (CpuRawSample) prevResult.getProbeRawSampleList().get(i);
                TbProbeSeries s = new TbProbeSeries();
                s.setHostname(conf.getHostname());
                s.setProbeType("cpu");
                s.setDevice(null);
                s.setSampleTms(curResult.getCollectTms());
                long diffTotal = cs.getTotalTime() - ps.getTotalTime();
                long diffIdle = cs.getIdleTime() - ps.getIdleTime();
                BigDecimal cpu = new BigDecimal(100.0 * (diffTotal - diffIdle) / diffTotal).setScale(1, RoundingMode.HALF_UP);
                s.setSampleValue(cpu);
                series.add(s);
            } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.MEM) {
                // mem saved in mebibyte, rounded to nearest integer
                MemRawSample cs = (MemRawSample) curResult.getProbeRawSampleList().get(i);
                TbProbeSeries s1 = new TbProbeSeries();
                s1.setHostname(conf.getHostname());
                s1.setProbeType("mem");
                s1.setDevice(null);
                s1.setSampleTms(curResult.getCollectTms());
                BigDecimal mem = new BigDecimal(cs.getMemUsed() / 1024.0 / 1024.0).setScale(0, RoundingMode.HALF_UP);
                s1.setSampleValue(mem);
                series.add(s1);
                TbProbeSeries s2 = new TbProbeSeries();
                s2.setHostname(conf.getHostname());
                s2.setProbeType("swap");
                s2.setDevice(null);
                s2.setSampleTms(curResult.getCollectTms());
                BigDecimal swap = new BigDecimal(cs.getSwapUsed() / 1024.0 / 1024.0).setScale(0, RoundingMode.HALF_UP);
                s2.setSampleValue(swap);
                series.add(s2);
                TbProbeSeries s3 = new TbProbeSeries();
                s3.setHostname(conf.getHostname());
                s3.setProbeType("cache");
                s3.setDevice(null);
                s3.setSampleTms(curResult.getCollectTms());
                BigDecimal cache = new BigDecimal(cs.getCacheUsed() / 1024.0 / 1024.0).setScale(0, RoundingMode.HALF_UP);
                s3.setSampleValue(cache);
                series.add(s3);
            } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.NET) {
                // net saved in kibibyte/s, rounded to nearest integer
                NetRawSample cs = (NetRawSample) curResult.getProbeRawSampleList().get(i);
                NetRawSample ps = (NetRawSample) prevResult.getProbeRawSampleList().get(i);
                long elapsedMsec = curResult.getCollectTms().getTime() - prevResult.getCollectTms().getTime();
                TbProbeSeries s1 = new TbProbeSeries();
                s1.setHostname(conf.getHostname());
                s1.setProbeType("net_tx");
                s1.setDevice(cs.getInterfaceName());
                s1.setSampleTms(curResult.getCollectTms());
                BigDecimal tx = new BigDecimal((cs.getTxBytes() - ps.getTxBytes()) / 1024.0 / elapsedMsec * 1000.0).setScale(0, RoundingMode.HALF_UP);
                s1.setSampleValue(tx);
                series.add(s1);
                TbProbeSeries s2 = new TbProbeSeries();
                s2.setHostname(conf.getHostname());
                s2.setProbeType("net_rx");
                s2.setDevice(cs.getInterfaceName());
                s2.setSampleTms(curResult.getCollectTms());
                BigDecimal rx = new BigDecimal((cs.getRxBytes() - ps.getRxBytes()) / 1024.0 / elapsedMsec * 1000.0).setScale(0, RoundingMode.HALF_UP);
                s2.setSampleValue(rx);
                series.add(s2);
            } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.DISK) {
                // net saved in mbyte/s, rounded to 1 significant digit
                DiskRawSample cs = (DiskRawSample) curResult.getProbeRawSampleList().get(i);
                DiskRawSample ps = (DiskRawSample) prevResult.getProbeRawSampleList().get(i);
                long elapsedMsec = curResult.getCollectTms().getTime() - prevResult.getCollectTms().getTime();
                TbProbeSeries s1 = new TbProbeSeries();
                s1.setHostname(conf.getHostname());
                s1.setProbeType("disk_read");
                s1.setDevice(cs.getDeviceName());
                s1.setSampleTms(curResult.getCollectTms());
                BigDecimal read = new BigDecimal((cs.getReadBytes() - ps.getReadBytes()) / 1024.0 / 1024.0 / elapsedMsec * 1000.0).setScale(1, RoundingMode.HALF_UP);
                s1.setSampleValue(read);
                series.add(s1);
                TbProbeSeries s2 = new TbProbeSeries();
                s2.setHostname(conf.getHostname());
                s2.setProbeType("disk_write");
                s2.setDevice(cs.getDeviceName());
                s2.setSampleTms(curResult.getCollectTms());
                BigDecimal write = new BigDecimal((cs.getWriteBytes() - ps.getWriteBytes()) / 1024.0 / 1024.0 / elapsedMsec * 1000.0).setScale(1, RoundingMode.HALF_UP);
                s2.setSampleValue(write);
                series.add(s2);
            } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.GPU) {
                GpuRawSample cs = (GpuRawSample) curResult.getProbeRawSampleList().get(i);
                TbProbeSeries s = new TbProbeSeries();
                s.setHostname(conf.getHostname());
                s.setProbeType("gpu");
                s.setDevice(null);
                s.setSampleTms(curResult.getCollectTms());
                s.setSampleValue(cs.getLoad());
                series.add(s);
            } else {
                throw new UnsupportedOperationException(String.format("Unsupported probe type: %s", conf.getProbeConfigList().get(i).getPrType()));
            }
        }
        if (log.isTraceEnabled()) {
            series.forEach((s) -> log.trace(s));
        }
        return series;
    }

    // creating html report
    private void doReport() throws ExecutionException {
        // reading from template
        String report = templateHtml.replace("XXX_TITLE_XXX", conf.getHostname());
        report = report.replace("XXX_HOSTNAME_XXX", conf.getHostname());
        report = report.replace("XXX_DATE_XXX", sdfHtml.format(curResult.getCollectTms()));

        // calculate reporting window
        Date fromTime = new Date(curResult.getCollectTms().getTime() - (conf.getReportHours() * HOUR_MS));

        // replacing placeholders
        report = report.replace("XXX_JSDATA_XXX\n", createJavascript(fromTime));
        report = report.replace("XXX_BODY_XXX\n", createHmtlBody());
        report = report.replace("XXX_TIMINGS_XXX",
                String.format("Time spent collecting samples: %s, writing samples: %s, generating report: %s",
                        smartElapsed(endCollectTime - startCollectTime, 0),
                        smartElapsed(endPersistTime - startPersistTime, 0),
                        smartElapsed(System.nanoTime() - startReportTime, 0)));

        // writing to file
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(conf.getWebPath().toString()), Charset.forName("UTF-8"), new OpenOption[]{WRITE, CREATE, TRUNCATE_EXISTING})) {
            bw.write(report);
        } catch (IOException ex) {
            throw new ExecutionException("I/O error while writing HTML report", ex);
        }
    }

    // body part
    private String createHmtlBody() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < conf.getProbeConfigList().size(); i++) {
            sb.append("<div id=\"div_chart").append(i + 1).append("\" class=\"chart-container ");
            if (conf.getProbeConfigList().get(i).getChSize() == ProbeConfiguration.ChartSize.FULL_SIZE) {
                sb.append("full-size");
            } else if (conf.getProbeConfigList().get(i).getChSize() == ProbeConfiguration.ChartSize.HALF_SIZE) {
                sb.append("half-size");
            } else {
                throw new UnsupportedOperationException(String.format("Unsupported chart size: %s", conf.getProbeConfigList().get(i).getChSize()));
            }
            sb.append("\"></div>").append(System.lineSeparator());
        }
        return sb.toString();
    }

    // javascript data and callback
    private String createJavascript(Date fromTime) throws ExecutionException {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = databaseStrategy.getConnection()) {
            for (int i = 0; i < conf.getProbeConfigList().size(); i++) {
                if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.LOAD) {
                    sb.append(createLoadCallback(i, conn, fromTime));
                } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.CPU) {
                    sb.append(createCpuCallback(i, conn, fromTime));
                } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.MEM) {
                    sb.append(createMemCallback(i, conn, fromTime));
                } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.NET) {
                    sb.append(createNetCallback(i, conn, fromTime));
                } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.DISK) {
                    sb.append(createDiskCallback(i, conn, fromTime));
                } else if (conf.getProbeConfigList().get(i).getPrType() == ProbeType.GPU) {
                    sb.append(createGpuCallback(i, conn, fromTime));
                } else {
                    throw new UnsupportedOperationException(String.format("Unsupported probe type: %s", conf.getProbeConfigList().get(i).getPrType()));
                }
            }
        } catch (SQLException ex) {
            throw new ExecutionException("Error while reading samples from DB", ex);
        }
        return sb.toString();
    }

    private String createLoadCallback(int idx, Connection conn, Date fromTime) throws SQLException {
        // callback definition
        StringBuilder sb = new StringBuilder();
        sb.append("google.charts.setOnLoadCallback(drawChart").append(idx + 1).append(");").append(System.lineSeparator());
        sb.append("function drawChart").append(idx + 1).append("() {").append(System.lineSeparator());
        sb.append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        // datatable and values from timeseries
        sb.append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append("data.addColumn('number', '1 min');").append(System.lineSeparator());
        sb.append("data.addColumn('number', '5 min');").append(System.lineSeparator());
        sb.append("data.addColumn('number', '15 min');").append(System.lineSeparator());
        sb.append(databaseStrategy.readLoadJsData(conn, conf.getHostname(), fromTime));
        // options
        sb.append(optsLoadJs).append(System.lineSeparator());
        // draw chart
        sb.append("var chart = new google.visualization.AreaChart(document.getElementById('div_chart").append(idx + 1).append("'));").append(System.lineSeparator());
        sb.append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString();
    }

    private String createCpuCallback(int idx, Connection conn, Date fromTime) throws SQLException {
        // callback definition
        StringBuilder sb = new StringBuilder();
        sb.append("google.charts.setOnLoadCallback(drawChart").append(idx + 1).append(");").append(System.lineSeparator());
        sb.append("function drawChart").append(idx + 1).append("() {").append(System.lineSeparator());
        sb.append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        // datatable and values from timeseries
        sb.append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'CPU');").append(System.lineSeparator());
        sb.append(databaseStrategy.readCpuJsData(conn, conf.getHostname(), fromTime));
        // options
        sb.append(optsCpuJs).append(System.lineSeparator());
        // draw chart
        sb.append("var chart = new google.visualization.AreaChart(document.getElementById('div_chart").append(idx + 1).append("'));").append(System.lineSeparator());
        sb.append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString();
    }

    private String createMemCallback(int idx, Connection conn, Date fromTime) throws SQLException {
        // callback definition
        StringBuilder sb = new StringBuilder();
        sb.append("google.charts.setOnLoadCallback(drawChart").append(idx + 1).append(");").append(System.lineSeparator());
        sb.append("function drawChart").append(idx + 1).append("() {").append(System.lineSeparator());
        sb.append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        // datatable and values from timeseries
        sb.append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'Physical memory');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'Swap');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'Cache');").append(System.lineSeparator());
        sb.append(databaseStrategy.readMemJsData(conn, conf.getHostname(), fromTime));
        // options
        sb.append(optsMemJs).append(System.lineSeparator());
        // draw chart
        sb.append("var chart = new google.visualization.AreaChart(document.getElementById('div_chart").append(idx + 1).append("'));").append(System.lineSeparator());
        sb.append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString();
    }

    private String createNetCallback(int idx, Connection conn, Date fromTime) throws SQLException {
        // callback definition
        StringBuilder sb = new StringBuilder();
        sb.append("google.charts.setOnLoadCallback(drawChart").append(idx + 1).append(");").append(System.lineSeparator());
        sb.append("function drawChart").append(idx + 1).append("() {").append(System.lineSeparator());
        sb.append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        // datatable and values from timeseries
        sb.append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'TX');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'RX');").append(System.lineSeparator());
        sb.append(databaseStrategy.readNetJsData(conn, conf.getHostname(), conf.getProbeConfigList().get(idx).getDevice(), fromTime));
        // options
        sb.append(optsNetJs).append(System.lineSeparator());
        // draw chart
        sb.append("var chart = new google.visualization.AreaChart(document.getElementById('div_chart").append(idx + 1).append("'));").append(System.lineSeparator());
        sb.append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString().replace("REPLACEME", conf.getProbeConfigList().get(idx).getLabel());
    }

    private String createDiskCallback(int idx, Connection conn, Date fromTime) throws SQLException {
        // callback definition
        StringBuilder sb = new StringBuilder();
        sb.append("google.charts.setOnLoadCallback(drawChart").append(idx + 1).append(");").append(System.lineSeparator());
        sb.append("function drawChart").append(idx + 1).append("() {").append(System.lineSeparator());
        sb.append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        // datatable and values from timeseries
        sb.append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'Read');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'Write');").append(System.lineSeparator());
        sb.append(databaseStrategy.readDiskJsData(conn, conf.getHostname(), conf.getProbeConfigList().get(idx).getDevice(), fromTime));
        // options
        sb.append(optsDiskJs).append(System.lineSeparator());
        // draw chart
        sb.append("var chart = new google.visualization.AreaChart(document.getElementById('div_chart").append(idx + 1).append("'));").append(System.lineSeparator());
        sb.append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString().replace("REPLACEME", conf.getProbeConfigList().get(idx).getLabel());
    }

    private String createGpuCallback(int idx, Connection conn, Date fromTime) throws SQLException {
        // callback definition
        StringBuilder sb = new StringBuilder();
        sb.append("google.charts.setOnLoadCallback(drawChart").append(idx + 1).append(");").append(System.lineSeparator());
        sb.append("function drawChart").append(idx + 1).append("() {").append(System.lineSeparator());
        sb.append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        // datatable and values from timeseries
        sb.append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append("data.addColumn('number', 'GPU');").append(System.lineSeparator());
        sb.append(databaseStrategy.readGpuJsData(conn, conf.getHostname(), fromTime));
        // options
        sb.append(optsGpuJs).append(System.lineSeparator());
        // draw chart
        sb.append("var chart = new google.visualization.AreaChart(document.getElementById('div_chart").append(idx + 1).append("'));").append(System.lineSeparator());
        sb.append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString();
    }

    private void doMaintenance() throws ExecutionException {
        try (Connection conn = databaseStrategy.getConnection()) {
            if (conf.getRetentionHours() > 0) {
                Date fromTime = new Date(curResult.getCollectTms().getTime() - (conf.getRetentionHours() * HOUR_MS));
                databaseStrategy.deleteTimeseries(conn, conf.getHostname(), fromTime);
            }
            databaseStrategy.doMaintenance(conn);
        } catch (SQLException ex) {
            throw new ExecutionException("Error while doing DB maintenance", ex);
        }
    }

}
