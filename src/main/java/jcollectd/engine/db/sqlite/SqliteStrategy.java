package jcollectd.engine.db.sqlite;

import jcollectd.engine.config.CollectConfiguration;
import jcollectd.engine.db.DatabaseStrategy;
import jcollectd.engine.db.model.TbProbeSeries;

import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import static jcollectd.common.ReportUtils.HTML_4X_INDENT;
import static jcollectd.engine.db.sqlite.SqliteUtils.*;

public class SqliteStrategy implements DatabaseStrategy {

    private final String connectionString;
    private final SimpleDateFormat sdfSqlite = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");

    public SqliteStrategy(CollectConfiguration conf) {
        connectionString = "jdbc:sqlite:" + conf.getDbPath().toString();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new UnsupportedOperationException("Error while loading Sqlite JDBC driver", ex);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(connectionString);
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(PRAGMA);
        }
        return conn;
    }

    @Override
    public void prepareSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(CREATE_TB_STMT);
            stmt.executeUpdate(CREATE_IDX_STMT);
        }
    }

    @Override
    public void persistTimeseries(Connection conn, ArrayList<TbProbeSeries> tmsList) throws SQLException {
        try (Statement stmt = conn.createStatement(); PreparedStatement pstmt = conn.prepareStatement(INS_STMT)) {
            stmt.executeUpdate(BEGIN_TRANS);
            for (TbProbeSeries s : tmsList) {
                pstmt.setString(1, s.getHostname());
                pstmt.setString(2, s.getProbeType());
                pstmt.setString(3, s.getDevice());
                pstmt.setString(4, sdfSqlite.format(s.getSampleTms()));
                pstmt.setBigDecimal(5, s.getSampleValue());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            stmt.executeUpdate(END_TRANS);
        }
    }

    @Override
    public String readLoadJsData(Connection conn, String hostname, Date fromTime) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_LOAD)) {
            stmt.setString(1, hostname);
            stmt.setString(2, sdfSqlite.format(fromTime));
            try (ResultSet rs = stmt.executeQuery()) {
                Calendar cal = Calendar.getInstance();
                cal.setLenient(false);
                while (rs.next()) {
                    try {
                        cal.setTime(sdfSqlite.parse(rs.getString(1)));
                    } catch (ParseException ex) {
                        // this should never happen, but just in case we skip the line
                        // no need to throw a RuntimeException and stop execution
                        continue;
                    }
                    sb.append(HTML_4X_INDENT).append("data.addRows([[new Date(");
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
        return sb.toString();
    }

    @Override
    public String readCpuJsData(Connection conn, String hostname, Date fromTime) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_CPU)) {
            stmt.setString(1, hostname);
            stmt.setString(2, sdfSqlite.format(fromTime));
            try (ResultSet rs = stmt.executeQuery()) {
                Calendar cal = Calendar.getInstance();
                cal.setLenient(false);
                while (rs.next()) {
                    try {
                        cal.setTime(sdfSqlite.parse(rs.getString(1)));
                    } catch (ParseException ex) {
                        // this should never happen, but just in case we skip the line
                        // no need to throw a RuntimeException and stop execution
                        continue;
                    }
                    sb.append(HTML_4X_INDENT).append("data.addRows([[new Date(");
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
        return sb.toString();
    }

    @Override
    public String readMemJsData(Connection conn, String hostname, Date fromTime) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_MEM)) {
            stmt.setString(1, hostname);
            stmt.setString(2, sdfSqlite.format(fromTime));
            try (ResultSet rs = stmt.executeQuery()) {
                Calendar cal = Calendar.getInstance();
                cal.setLenient(false);
                while (rs.next()) {
                    try {
                        cal.setTime(sdfSqlite.parse(rs.getString(1)));
                    } catch (ParseException ex) {
                        // this should never happen, but just in case we skip the line
                        // no need to throw a RuntimeException and stop execution
                        continue;
                    }
                    sb.append(HTML_4X_INDENT).append("data.addRows([[new Date(");
                    sb.append(cal.get(Calendar.YEAR)).append(",");
                    sb.append(cal.get(Calendar.MONTH)).append(",");
                    sb.append(cal.get(Calendar.DAY_OF_MONTH)).append(",");
                    sb.append(cal.get(Calendar.HOUR_OF_DAY)).append(",");
                    sb.append(cal.get(Calendar.MINUTE)).append(",");
                    sb.append(cal.get(Calendar.SECOND)).append("),");
                    sb.append(rs.getString(2)).append(",");
                    // in stacking area chart, we convert zeroes to null, to prevent 0-height slices to cover underneath values
                    sb.append("0.0".equals(rs.getString(3)) ? "null" : rs.getString(3)).append(",");
                    sb.append("0.0".equals(rs.getString(4)) ? "null" : rs.getString(4));
                    sb.append("]]);");
                    sb.append(System.lineSeparator());
                }
            }
        }
        return sb.toString();
    }

    @Override
    public String readNetJsData(Connection conn, String hostname, String device, Date fromTime) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_NET)) {
            stmt.setString(1, hostname);
            stmt.setString(2, device);
            stmt.setString(3, sdfSqlite.format(fromTime));
            try (ResultSet rs = stmt.executeQuery()) {
                Calendar cal = Calendar.getInstance();
                cal.setLenient(false);
                while (rs.next()) {
                    try {
                        cal.setTime(sdfSqlite.parse(rs.getString(1)));
                    } catch (ParseException ex) {
                        // this should never happen, but just in case we skip the line
                        // no need to throw a RuntimeException and stop execution
                        continue;
                    }
                    sb.append(HTML_4X_INDENT).append("data.addRows([[new Date(");
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
        return sb.toString();
    }

    @Override
    public String readDiskJsData(Connection conn, String hostname, String device, Date fromTime) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_DISK)) {
            stmt.setString(1, hostname);
            stmt.setString(2, device);
            stmt.setString(3, sdfSqlite.format(fromTime));
            try (ResultSet rs = stmt.executeQuery()) {
                Calendar cal = Calendar.getInstance();
                cal.setLenient(false);
                while (rs.next()) {
                    try {
                        cal.setTime(sdfSqlite.parse(rs.getString(1)));
                    } catch (ParseException ex) {
                        // this should never happen, but just in case we skip the line
                        // no need to throw a RuntimeException and stop execution
                        continue;
                    }
                    sb.append(HTML_4X_INDENT).append("data.addRows([[new Date(");
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
        return sb.toString();
    }

    @Override
    public String readGpuJsData(Connection conn, String hostname, Date fromTime) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_GPU)) {
            stmt.setString(1, hostname);
            stmt.setString(2, sdfSqlite.format(fromTime));
            try (ResultSet rs = stmt.executeQuery()) {
                Calendar cal = Calendar.getInstance();
                cal.setLenient(false);
                while (rs.next()) {
                    try {
                        cal.setTime(sdfSqlite.parse(rs.getString(1)));
                    } catch (ParseException ex) {
                        // this should never happen, but just in case we skip the line
                        // no need to throw a RuntimeException and stop execution
                        continue;
                    }
                    sb.append(HTML_4X_INDENT).append("data.addRows([[new Date(");
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
        return sb.toString();
    }

    @Override
    public void deleteTimeseries(Connection conn, String hostname, Date fromTime) throws SQLException {
        try (Statement stmt = conn.createStatement(); PreparedStatement pstmt = conn.prepareStatement(DEL_STMT)) {
            stmt.executeUpdate(BEGIN_TRANS);
            pstmt.setString(1, hostname);
            pstmt.setString(2, sdfSqlite.format(fromTime));
            pstmt.executeUpdate();
            stmt.executeUpdate(END_TRANS);
        }
    }

    @Override
    public void doMaintenance(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(VACUUM);
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(ANALYZE);
        }
    }

}
