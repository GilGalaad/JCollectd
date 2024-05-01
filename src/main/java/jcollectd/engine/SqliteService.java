package jcollectd.engine;

import jcollectd.common.dto.sample.*;
import lombok.extern.log4j.Log4j2;

import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Log4j2
public class SqliteService implements AutoCloseable {

    private static final String CONNECTION_URL = "jdbc:sqlite:samples.db";
    private static final String BEGIN_TRANSACTION = "BEGIN IMMEDIATE TRANSACTION";
    private static final String COMMIT = "COMMIT";

    private final Connection conn;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new UnsupportedOperationException("Error while loading Sqlite JDBC driver", ex);
        }
    }

    public SqliteService() throws SQLException {
        conn = getConnection();
    }

    @Override
    public void close() throws SQLException {
        if (conn != null) {
            conn.close();
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(CONNECTION_URL);
    }

    public void initializeDatabase() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(BEGIN_TRANSACTION);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tb_load_sample (
                        sample_tms TEXT PRIMARY KEY,
                        load1 TEXT NOT NULL,
                        load5 TEXT NOT NULL,
                        load15 TEXT NOT NULL
                    ) WITHOUT ROWID""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tb_cpu_sample (
                        sample_tms TEXT PRIMARY KEY,
                        load TEXT NOT NULL
                    ) WITHOUT ROWID""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tb_mem_sample (
                        sample_tms TEXT PRIMARY KEY,
                        mem TEXT NOT NULL,
                        cache TEXT NOT NULL,
                        swap TEXT NOT NULL
                    ) WITHOUT ROWID""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tb_net_sample (
                        sample_tms TEXT NOT NULL,
                        device TEXT NOT NULL,
                        rx TEXT NOT NULL,
                        tx TEXT NOT NULL,
                        PRIMARY KEY (sample_tms, device)
                    ) WITHOUT ROWID""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tb_disk_sample (
                        sample_tms TEXT NOT NULL,
                        device TEXT NOT NULL,
                        read TEXT NOT NULL,
                        write TEXT NOT NULL,
                        PRIMARY KEY (sample_tms, device)
                    ) WITHOUT ROWID""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tb_gpu_sample (
                        sample_tms TEXT PRIMARY KEY,
                        load TEXT NOT NULL
                    ) WITHOUT ROWID""");

            stmt.executeUpdate(COMMIT);
        }
    }

    public void persistSamples(List<ComputedSample> samples) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(BEGIN_TRANSACTION);

            List<LoadComputedSample> loads = samples.stream().filter(i -> i instanceof LoadComputedSample).map(i -> (LoadComputedSample) i).toList();
            if (!loads.isEmpty()) {
                try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO tb_load_sample (sample_tms, load1, load5, load15) VALUES (?,?,?,?)")) {
                    for (var load : loads) {
                        pstmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(load.getSampleTms()));
                        pstmt.setBigDecimal(2, load.getLoad1());
                        pstmt.setBigDecimal(3, load.getLoad5());
                        pstmt.setBigDecimal(4, load.getLoad15());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
            }

            List<CpuComputedSample> cpus = samples.stream().filter(i -> i instanceof CpuComputedSample).map(i -> (CpuComputedSample) i).toList();
            if (!cpus.isEmpty()) {
                try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO tb_cpu_sample (sample_tms, load) VALUES (?,?)")) {
                    for (var cpu : cpus) {
                        pstmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(cpu.getSampleTms()));
                        pstmt.setBigDecimal(2, cpu.getLoad());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
            }

            List<MemComputedSample> mems = samples.stream().filter(i -> i instanceof MemComputedSample).map(i -> (MemComputedSample) i).toList();
            if (!mems.isEmpty()) {
                try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO tb_mem_sample (sample_tms, mem, cache, swap) VALUES (?,?,?,?)")) {
                    for (var mem : mems) {
                        pstmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(mem.getSampleTms()));
                        pstmt.setBigDecimal(2, mem.getMem());
                        pstmt.setBigDecimal(3, mem.getCache());
                        pstmt.setBigDecimal(4, mem.getSwap());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
            }

            List<NetComputedSample> nets = samples.stream().filter(i -> i instanceof NetComputedSample).map(i -> (NetComputedSample) i).toList();
            if (!nets.isEmpty()) {
                try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO tb_net_sample (sample_tms, device, rx, tx) VALUES (?,?,?,?)")) {
                    for (var net : nets) {
                        pstmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(net.getSampleTms()));
                        pstmt.setString(2, net.getDevice());
                        pstmt.setBigDecimal(3, net.getRx());
                        pstmt.setBigDecimal(4, net.getTx());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
            }

            List<DiskComputedSample> disks = samples.stream().filter(i -> i instanceof DiskComputedSample).map(i -> (DiskComputedSample) i).toList();
            if (!disks.isEmpty()) {
                try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO tb_disk_sample (sample_tms, device, read, write) VALUES (?,?,?,?)")) {
                    for (var disk : disks) {
                        pstmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(disk.getSampleTms()));
                        pstmt.setString(2, disk.getDevice());
                        pstmt.setBigDecimal(3, disk.getRead());
                        pstmt.setBigDecimal(4, disk.getWrite());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
            }

            List<GpuComputedSample> gpus = samples.stream().filter(i -> i instanceof GpuComputedSample).map(i -> (GpuComputedSample) i).toList();
            if (!gpus.isEmpty()) {
                try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO tb_gpu_sample (sample_tms, load) VALUES (?,?)")) {
                    for (var gpu : gpus) {
                        pstmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(gpu.getSampleTms()));
                        pstmt.setBigDecimal(2, gpu.getLoad());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
            }

            stmt.executeUpdate(COMMIT);
        }
    }

}
