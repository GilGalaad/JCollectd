package engine.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

public abstract class DatabaseStrategy {

    public abstract Connection getConnection() throws SQLException;

    public abstract void prepareSchema(Connection conn) throws SQLException;

    public abstract void persistTimeseries(Connection conn, ArrayList<TbProbeSeries> tmsList) throws SQLException;

    public abstract String readLoadJsData(Connection conn, String hostname, Date fromTime) throws SQLException;

    public abstract String readCpuJsData(Connection conn, String hostname, Date fromTime) throws SQLException;

    public abstract String readMemJsData(Connection conn, String hostname, Date fromTime) throws SQLException;

    public abstract String readNetJsData(Connection conn, String hostname, String device, Date fromTime) throws SQLException;

    public abstract String readDiskJsData(Connection conn, String hostname, String device, Date fromTime) throws SQLException;

    public abstract void deleteTimeseries(Connection conn, String hostname, Date fromTime) throws SQLException;

    public abstract void doMaintenance(Connection conn) throws SQLException;

}
