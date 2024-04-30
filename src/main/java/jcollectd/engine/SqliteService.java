package jcollectd.engine;

import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Log4j2
public class SqliteService {

    private static final String CONNECTION_URL = "jdbc:sqlite:samples.db";

    public SqliteService() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new UnsupportedOperationException("Error while loading Sqlite JDBC driver", ex);
        }
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(CONNECTION_URL);
        conn.setAutoCommit(true);
        return conn;
    }

    public void initializeDatabase(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tb_load_sample (
                        sample_tms TEXT PRIMARY KEY,
                        load1 TEXT NOT NULL,
                        load5 TEXT NOT NULL,
                        load15 TEXT NOT NULL
                    ) WITHOUT ROWID
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tb_cpu_sample (
                        sample_tms TEXT PRIMARY KEY,
                        load TEXT NOT NULL
                    ) WITHOUT ROWID
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tb_mem_sample (
                        sample_tms TEXT PRIMARY KEY,
                        mem TEXT NOT NULL,
                        cache TEXT NOT NULL,
                        swap TEXT NOT NULL
                    ) WITHOUT ROWID
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tb_net_sample (
                        sample_tms TEXT NOT NULL,
                        device TEXT NOT NULL,
                        rx TEXT NOT NULL,
                        tx TEXT NOT NULL,
                        PRIMARY KEY (sample_tms, device)
                    ) WITHOUT ROWID
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tb_disk_sample (
                        sample_tms TEXT NOT NULL,
                        device TEXT NOT NULL,
                        read TEXT NOT NULL,
                        write TEXT NOT NULL,
                        PRIMARY KEY (sample_tms, device)
                    ) WITHOUT ROWID
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tb_gpu_sample (
                        sample_tms TEXT PRIMARY KEY,
                        load TEXT NOT NULL
                    ) WITHOUT ROWID
                    """);
        }
    }

}
