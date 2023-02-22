package jcollectd.engine;

import jcollectd.common.exception.ExecutionException;
import jcollectd.engine.collect.CollectStrategy;
import jcollectd.engine.collect.FreeBSDCollectStrategy;
import jcollectd.engine.collect.LinuxCollectStrategy;
import jcollectd.engine.config.ChartSize;
import jcollectd.engine.config.CollectConfiguration;
import jcollectd.engine.db.DatabaseStrategy;
import jcollectd.engine.db.model.TbProbeSeries;
import jcollectd.engine.db.sqlite.SqliteStrategy;
import jcollectd.engine.sample.*;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import static java.nio.file.StandardOpenOption.*;
import static jcollectd.common.CommonUtils.HOUR_MS;
import static jcollectd.common.CommonUtils.smartElapsed;
import static jcollectd.common.ReportUtils.*;

@Log4j2
public class CollectEngine {

    // configuration
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
    private long startWriteTime;
    private long WriteFlushTime;
    private long startCleanTime;
    private long endCleanTime;
    private Date lastMaintenance;

    // dates
    private final SimpleDateFormat sdfHtml = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    public CollectEngine(CollectConfiguration conf) {
        this.conf = conf;
        this.samplingInterval = conf.getInterval() * 1000L;
        lastMaintenance = new Date();

        switch (conf.getOs()) {
            case FREEBSD -> collectStrategy = new FreeBSDCollectStrategy();
            case LINUX -> collectStrategy = new LinuxCollectStrategy();
            default -> throw new UnsupportedOperationException(String.format("Unsupported Operating System: %s", conf.getOs()));
        }

        switch (conf.getDbEngine()) {
            case SQLITE -> databaseStrategy = new SqliteStrategy(conf);
            default -> throw new UnsupportedOperationException(String.format("Unsupported Database engine: %s", conf.getDbEngine()));
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
            curResult = new CollectResult(conf.getProbes().size());
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
                String report = doReport();
                endReportTime = System.nanoTime();
                // replacing timings
                report = report.replace("XXX_TIMINGS_XXX",
                        String.format("Time spent collecting samples: %s, writing samples: %s, generating report: %s",
                                smartElapsed(endCollectTime - startCollectTime, 0),
                                smartElapsed(endPersistTime - startPersistTime, 0),
                                smartElapsed(endReportTime - startReportTime, 0)));
                if (log.isDebugEnabled()) {
                    log.debug("Reporting time: {}", smartElapsed(endReportTime - startReportTime));
                }

                // writing to disk
                startWriteTime = System.nanoTime();
                doWriteReport(report);
                WriteFlushTime = System.nanoTime();
                if (log.isDebugEnabled()) {
                    log.debug("Writing to disk time: {}", smartElapsed(WriteFlushTime - startWriteTime));
                }
            }

            // janitor work, once an hour
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
        for (int i = 0; i < conf.getProbes().size(); i++) {
            switch (conf.getProbes().get(i).getProbeType()) {
                case LOAD -> curResult.getSamples().add(i, collectStrategy.collectLoadAvg());
                case CPU -> curResult.getSamples().add(i, collectStrategy.collectCpu());
                case MEM -> curResult.getSamples().add(i, collectStrategy.collectMem());
                case NET -> curResult.getSamples().add(i, collectStrategy.collectNet(conf.getProbes().get(i).getDevice()));
                case DISK -> curResult.getSamples().add(i, collectStrategy.collectDisk(conf.getProbes().get(i).getDevice()));
                case ZFS -> curResult.getSamples().add(i, collectStrategy.collectZFS(conf.getProbes().get(i).getDevice()));
                case GPU -> curResult.getSamples().add(i, collectStrategy.collectGpu());
                default -> throw new UnsupportedOperationException(String.format("Unsupported probe type: %s", conf.getProbes().get(i).getProbeType()));
            }
            if (log.isTraceEnabled()) {
                log.trace(curResult.getSamples().get(i).toString());
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
        for (int i = 0; i < conf.getProbes().size(); i++) {
            switch (conf.getProbes().get(i).getProbeType()) {
                case LOAD -> {
                    LoadRawSample cs = (LoadRawSample) curResult.getSamples().get(i);
                    series.add(new TbProbeSeries(conf.getHostname(), "load1m", curResult.getCollectTms(), cs.getLoad1minute()));
                    series.add(new TbProbeSeries(conf.getHostname(), "load5m", curResult.getCollectTms(), cs.getLoad5minute()));
                    series.add(new TbProbeSeries(conf.getHostname(), "load15m", curResult.getCollectTms(), cs.getLoad15minute()));
                }
                case CPU -> {
                    // cpu saved in percent, rounded to 1 significant digit
                    CpuRawSample cs = (CpuRawSample) curResult.getSamples().get(i);
                    CpuRawSample ps = (CpuRawSample) prevResult.getSamples().get(i);
                    long diffTotal = cs.getTotalTime() - ps.getTotalTime();
                    long diffIdle = cs.getIdleTime() - ps.getIdleTime();
                    BigDecimal cpu = new BigDecimal(100.0 * (diffTotal - diffIdle) / diffTotal).setScale(1, RoundingMode.HALF_UP);
                    series.add(new TbProbeSeries(conf.getHostname(), "cpu", curResult.getCollectTms(), cpu));
                }
                case MEM -> {
                    // mem saved in mebibyte, rounded to nearest integer
                    MemRawSample cs = (MemRawSample) curResult.getSamples().get(i);
                    BigDecimal mem = BigDecimal.valueOf(cs.getMemUsed() / 1024.0 / 1024.0).setScale(0, RoundingMode.HALF_UP);
                    BigDecimal swap = BigDecimal.valueOf(cs.getSwapUsed() / 1024.0 / 1024.0).setScale(0, RoundingMode.HALF_UP);
                    BigDecimal cache = BigDecimal.valueOf(cs.getCacheUsed() / 1024.0 / 1024.0).setScale(0, RoundingMode.HALF_UP);
                    series.add(new TbProbeSeries(conf.getHostname(), "mem", curResult.getCollectTms(), mem));
                    series.add(new TbProbeSeries(conf.getHostname(), "swap", curResult.getCollectTms(), swap));
                    series.add(new TbProbeSeries(conf.getHostname(), "cache", curResult.getCollectTms(), cache));
                }
                case NET -> {
                    // net saved in kibibyte/s, rounded to nearest integer
                    NetRawSample cs = (NetRawSample) curResult.getSamples().get(i);
                    NetRawSample ps = (NetRawSample) prevResult.getSamples().get(i);
                    long elapsedMsec = curResult.getCollectTms().getTime() - prevResult.getCollectTms().getTime();
                    BigDecimal tx = new BigDecimal((cs.getTxBytes() - ps.getTxBytes()) / 1024.0 / elapsedMsec * 1000.0).setScale(0, RoundingMode.HALF_UP);
                    tx = tx.compareTo(BigDecimal.ZERO) >= 0 ? tx : BigDecimal.ZERO;
                    BigDecimal rx = new BigDecimal((cs.getRxBytes() - ps.getRxBytes()) / 1024.0 / elapsedMsec * 1000.0).setScale(0, RoundingMode.HALF_UP);
                    rx = rx.compareTo(BigDecimal.ZERO) >= 0 ? rx : BigDecimal.ZERO;
                    series.add(new TbProbeSeries(conf.getHostname(), "net_tx", cs.getDevice(), curResult.getCollectTms(), tx));
                    series.add(new TbProbeSeries(conf.getHostname(), "net_rx", cs.getDevice(), curResult.getCollectTms(), rx));
                }
                case DISK, ZFS -> {
                    // disk saved in mbyte/s, rounded to 1 significant digit
                    DiskRawSample cs = (DiskRawSample) curResult.getSamples().get(i);
                    DiskRawSample ps = (DiskRawSample) prevResult.getSamples().get(i);
                    long elapsedMsec = curResult.getCollectTms().getTime() - prevResult.getCollectTms().getTime();
                    BigDecimal read = new BigDecimal((cs.getReadBytes() - ps.getReadBytes()) / 1024.0 / 1024.0 / elapsedMsec * 1000.0).setScale(1, RoundingMode.HALF_UP);
                    read = read.compareTo(BigDecimal.ZERO) >= 0 ? read : BigDecimal.ZERO;
                    BigDecimal write = new BigDecimal((cs.getWriteBytes() - ps.getWriteBytes()) / 1024.0 / 1024.0 / elapsedMsec * 1000.0).setScale(1, RoundingMode.HALF_UP);
                    write = write.compareTo(BigDecimal.ZERO) >= 0 ? write : BigDecimal.ZERO;
                    series.add(new TbProbeSeries(conf.getHostname(), "disk_read", cs.getDevice(), curResult.getCollectTms(), read));
                    series.add(new TbProbeSeries(conf.getHostname(), "disk_write", cs.getDevice(), curResult.getCollectTms(), write));
                }
                case GPU -> {
                    GpuRawSample cs = (GpuRawSample) curResult.getSamples().get(i);
                    series.add(new TbProbeSeries(conf.getHostname(), "gpu", curResult.getCollectTms(), cs.getLoad()));
                }
                default -> throw new UnsupportedOperationException(String.format("Unsupported probe type: %s", conf.getProbes().get(i).getProbeType()));
            }
        }
        if (log.isTraceEnabled()) {
            series.forEach(log::trace);
        }
        return series;
    }

    // creating html report
    private String doReport() throws ExecutionException {
        // reading from template
        String report = templateHtml.replace("XXX_TITLE_XXX", conf.getHostname());
        report = report.replace("XXX_HOSTNAME_XXX", conf.getHostname());
        report = report.replace("XXX_DATE_XXX", sdfHtml.format(curResult.getCollectTms()));

        // calculate reporting window
        Date fromTime = new Date(curResult.getCollectTms().getTime() - (conf.getReportHours() * HOUR_MS));

        // replacing placeholders
        report = report.replace("XXX_JSDATA_XXX", createJavascript(fromTime).trim());
        report = report.replace("XXX_BODY_XXX", createHmtlBody().trim());
        return report;
    }

    private void doWriteReport(String report) throws ExecutionException {
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(conf.getWebPath().toString()), StandardCharsets.UTF_8, WRITE, CREATE, TRUNCATE_EXISTING)) {
            bw.write(report);
        } catch (IOException ex) {
            throw new ExecutionException("I/O error while writing HTML report", ex);
        }
    }

    // body part
    private String createHmtlBody() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < conf.getProbes().size(); i++) {
            sb.append(HTML_3X_INDENT).append("<div id=\"div_chart").append(i + 1).append("\" class=\"chart-container ");
            if (conf.getProbes().get(i).getChartSize() == ChartSize.FULL_SIZE) {
                sb.append("full-size");
            } else if (conf.getProbes().get(i).getChartSize() == ChartSize.HALF_SIZE) {
                sb.append("half-size");
            } else {
                throw new UnsupportedOperationException(String.format("Unsupported chart size: %s", conf.getProbes().get(i).getChartSize()));
            }
            sb.append("\"></div>").append(System.lineSeparator());
        }
        return sb.toString();
    }

    // javascript data and callback
    private String createJavascript(Date fromTime) throws ExecutionException {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = databaseStrategy.getConnection()) {
            for (int i = 0; i < conf.getProbes().size(); i++) {
                switch (conf.getProbes().get(i).getProbeType()) {
                    case LOAD -> sb.append(createLoadCallback(i, conn, fromTime));
                    case CPU -> sb.append(createCpuCallback(i, conn, fromTime));
                    case MEM -> sb.append(createMemCallback(i, conn, fromTime));
                    case NET -> sb.append(createNetCallback(i, conn, fromTime));
                    case DISK, ZFS -> sb.append(createDiskCallback(i, conn, fromTime));
                    case GPU -> sb.append(createGpuCallback(i, conn, fromTime));
                    default -> throw new UnsupportedOperationException(String.format("Unsupported probe type: %s", conf.getProbes().get(i).getProbeType()));
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
        sb.append(HTML_3X_INDENT).append("google.charts.setOnLoadCallback(drawChart").append(idx + 1).append(");").append(System.lineSeparator());
        sb.append(HTML_3X_INDENT).append("function drawChart").append(idx + 1).append("() {").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        // datatable and values from timeseries
        sb.append(HTML_4X_INDENT).append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("data.addColumn('number', '1 min');").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("data.addColumn('number', '5 min');").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("data.addColumn('number', '15 min');").append(System.lineSeparator()).append(System.lineSeparator());
        sb.append(databaseStrategy.readLoadJsData(conn, conf.getHostname(), fromTime)).append(System.lineSeparator());
        // options
        sb.append(optsLoadJs).append(System.lineSeparator());
        // draw chart
        sb.append(HTML_4X_INDENT).append("var chart = new google.visualization.AreaChart(document.getElementById('div_chart").append(idx + 1).append("'));").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append(HTML_3X_INDENT).append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString();
    }

    private String createCpuCallback(int idx, Connection conn, Date fromTime) throws SQLException {
        // callback definition
        StringBuilder sb = new StringBuilder();
        sb.append(HTML_3X_INDENT).append("google.charts.setOnLoadCallback(drawChart").append(idx + 1).append(");").append(System.lineSeparator());
        sb.append(HTML_3X_INDENT).append("function drawChart").append(idx + 1).append("() {").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        // datatable and values from timeseries
        sb.append(HTML_4X_INDENT).append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("data.addColumn('number', 'CPU');").append(System.lineSeparator()).append(System.lineSeparator());
        sb.append(databaseStrategy.readCpuJsData(conn, conf.getHostname(), fromTime)).append(System.lineSeparator());
        // options
        sb.append(optsCpuJs).append(System.lineSeparator());
        // draw chart
        sb.append(HTML_4X_INDENT).append("var chart = new google.visualization.AreaChart(document.getElementById('div_chart").append(idx + 1).append("'));").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append(HTML_3X_INDENT).append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString();
    }

    private String createMemCallback(int idx, Connection conn, Date fromTime) throws SQLException {
        // callback definition
        StringBuilder sb = new StringBuilder();
        sb.append(HTML_3X_INDENT).append("google.charts.setOnLoadCallback(drawChart").append(idx + 1).append(");").append(System.lineSeparator());
        sb.append(HTML_3X_INDENT).append("function drawChart").append(idx + 1).append("() {").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        // datatable and values from timeseries
        sb.append(HTML_4X_INDENT).append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("data.addColumn('number', 'Physical memory');").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("data.addColumn('number', 'Swap');").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("data.addColumn('number', 'Cache');").append(System.lineSeparator()).append(System.lineSeparator());
        sb.append(databaseStrategy.readMemJsData(conn, conf.getHostname(), fromTime)).append(System.lineSeparator());
        // options
        sb.append(optsMemJs).append(System.lineSeparator());
        // draw chart
        sb.append(HTML_4X_INDENT).append("var chart = new google.visualization.AreaChart(document.getElementById('div_chart").append(idx + 1).append("'));").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append(HTML_3X_INDENT).append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString();
    }

    private String createNetCallback(int idx, Connection conn, Date fromTime) throws SQLException {
        // callback definition
        StringBuilder sb = new StringBuilder();
        sb.append(HTML_3X_INDENT).append("google.charts.setOnLoadCallback(drawChart").append(idx + 1).append(");").append(System.lineSeparator());
        sb.append(HTML_3X_INDENT).append("function drawChart").append(idx + 1).append("() {").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        // datatable and values from timeseries
        sb.append(HTML_4X_INDENT).append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("data.addColumn('number', 'TX');").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("data.addColumn('number', 'RX');").append(System.lineSeparator()).append(System.lineSeparator());
        sb.append(databaseStrategy.readNetJsData(conn, conf.getHostname(), conf.getProbes().get(idx).getDevice(), fromTime)).append(System.lineSeparator());
        // options
        sb.append(optsNetJs).append(System.lineSeparator());
        // draw chart
        sb.append(HTML_4X_INDENT).append("var chart = new google.visualization.AreaChart(document.getElementById('div_chart").append(idx + 1).append("'));").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append(HTML_3X_INDENT).append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString().replace("REPLACEME", conf.getProbes().get(idx).getLabel());
    }

    private String createDiskCallback(int idx, Connection conn, Date fromTime) throws SQLException {
        // callback definition
        StringBuilder sb = new StringBuilder();
        sb.append(HTML_3X_INDENT).append("google.charts.setOnLoadCallback(drawChart").append(idx + 1).append(");").append(System.lineSeparator());
        sb.append(HTML_3X_INDENT).append("function drawChart").append(idx + 1).append("() {").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        // datatable and values from timeseries
        sb.append(HTML_4X_INDENT).append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("data.addColumn('number', 'Read');").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("data.addColumn('number', 'Write');").append(System.lineSeparator()).append(System.lineSeparator());
        sb.append(databaseStrategy.readDiskJsData(conn, conf.getHostname(), conf.getProbes().get(idx).getDevice(), fromTime)).append(System.lineSeparator());
        // options
        sb.append(optsDiskJs).append(System.lineSeparator());
        // draw chart
        sb.append(HTML_4X_INDENT).append("var chart = new google.visualization.AreaChart(document.getElementById('div_chart").append(idx + 1).append("'));").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append(HTML_3X_INDENT).append("}").append(System.lineSeparator()).append(System.lineSeparator());
        return sb.toString().replace("REPLACEME", conf.getProbes().get(idx).getLabel());
    }

    private String createGpuCallback(int idx, Connection conn, Date fromTime) throws SQLException {
        // callback definition
        StringBuilder sb = new StringBuilder();
        sb.append(HTML_3X_INDENT).append("google.charts.setOnLoadCallback(drawChart").append(idx + 1).append(");").append(System.lineSeparator());
        sb.append(HTML_3X_INDENT).append("function drawChart").append(idx + 1).append("() {").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("var data = new google.visualization.DataTable();").append(System.lineSeparator());
        // datatable and values from timeseries
        sb.append(HTML_4X_INDENT).append("data.addColumn('datetime', 'Time');").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("data.addColumn('number', 'GPU');").append(System.lineSeparator()).append(System.lineSeparator());
        sb.append(databaseStrategy.readGpuJsData(conn, conf.getHostname(), fromTime)).append(System.lineSeparator());
        // options
        sb.append(optsGpuJs).append(System.lineSeparator());
        // draw chart
        sb.append(HTML_4X_INDENT).append("var chart = new google.visualization.AreaChart(document.getElementById('div_chart").append(idx + 1).append("'));").append(System.lineSeparator());
        sb.append(HTML_4X_INDENT).append("chart.draw(data, options);").append(System.lineSeparator());
        sb.append(HTML_3X_INDENT).append("}").append(System.lineSeparator()).append(System.lineSeparator());
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
