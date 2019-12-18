package engine.db;

import engine.db.model.TbProbeSeries;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

public interface DatabaseStrategy {

    Connection getConnection() throws SQLException;

    void prepareSchema(Connection conn) throws SQLException;

    void persistTimeseries(Connection conn, ArrayList<TbProbeSeries> tmsList) throws SQLException;

    String readLoadJsData(Connection conn, String hostname, Date fromTime) throws SQLException;

    String readCpuJsData(Connection conn, String hostname, Date fromTime) throws SQLException;

    String readMemJsData(Connection conn, String hostname, Date fromTime) throws SQLException;

    String readNetJsData(Connection conn, String hostname, String device, Date fromTime) throws SQLException;

    String readDiskJsData(Connection conn, String hostname, String device, Date fromTime) throws SQLException;

    void deleteTimeseries(Connection conn, String hostname, Date fromTime) throws SQLException;

    void doMaintenance(Connection conn) throws SQLException;

}
