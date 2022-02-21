package engine.db.sqlite;

public class SqliteUtils {

    private SqliteUtils() {
    }

    protected static final String CREATE_TB_STMT = "CREATE TABLE IF NOT EXISTS tb_probe_series (\n"
                                                   + "id_sample INTEGER PRIMARY KEY,\n"
                                                   + "hostname TEXT NOT NULL,\n"
                                                   + "probe_type TEXT NOT NULL,\n"
                                                   + "device TEXT,\n"
                                                   + "sample_tms TEXT NOT NULL,\n"
                                                   + "sample_value REAL NOT NULL\n"
                                                   + ")";

    protected static final String CREATE_IDX_STMT = "CREATE UNIQUE INDEX IF NOT EXISTS idx_probe_series\n"
                                                    + "ON tb_probe_series (hostname, probe_type, device, sample_tms)";

    protected static final String INS_STMT = "INSERT INTO tb_probe_series (hostname, probe_type, device, sample_tms, sample_value)\n"
                                             + "VALUES (?,?,?,?,?)";

    protected static final String DEL_STMT = "DELETE FROM tb_probe_series\n"
                                             + "WHERE hostname = ?\n"
                                             + "AND sample_tms <= ?";

    protected static final String BEGIN_TRANS = "BEGIN TRANSACTION";

    protected static final String END_TRANS = "END TRANSACTION";

    protected static final String ANALYZE = "ANALYZE";

    protected static final String VACUUM = "VACUUM";

    protected static final String PRAGMA = "PRAGMA busy_timeout = 5000";

    protected static final String SELECT_LOAD = "SELECT sample_tms,\n"
                                                + "MAX(CASE WHEN probe_type = 'load1m' THEN sample_value ELSE NULL END) load1m,\n"
                                                + "MAX(CASE WHEN probe_type = 'load5m' THEN sample_value ELSE NULL END) load5m,\n"
                                                + "MAX(CASE WHEN probe_type = 'load15m' THEN sample_value ELSE NULL END) load15m\n"
                                                + "FROM tb_probe_series\n"
                                                + "WHERE hostname = ?\n"
                                                + "AND (probe_type = 'load1m' OR probe_type = 'load5m' OR probe_type = 'load15m')\n"
                                                + "AND device IS NULL\n"
                                                + "AND sample_tms > ?\n"
                                                + "GROUP BY sample_tms ORDER BY sample_tms";

    protected static final String SELECT_CPU = "SELECT sample_tms, sample_value\n"
                                               + "FROM tb_probe_series\n"
                                               + "WHERE hostname = ?\n"
                                               + "AND probe_type = 'cpu'\n"
                                               + "AND device IS NULL\n"
                                               + "AND sample_tms > ?\n"
                                               + "ORDER BY sample_tms";

    protected static final String SELECT_MEM = "SELECT sample_tms,\n"
                                               + "MAX(CASE WHEN probe_type = 'mem' THEN sample_value ELSE NULL END) mem,\n"
                                               + "MAX(CASE WHEN probe_type = 'swap' THEN sample_value ELSE NULL END) swap,\n"
                                               + "MAX(CASE WHEN probe_type = 'cache' THEN sample_value ELSE NULL END) cache\n"
                                               + "FROM tb_probe_series\n"
                                               + "WHERE hostname = ?\n"
                                               + "AND (probe_type = 'mem' OR probe_type = 'swap' OR probe_type = 'cache')\n"
                                               + "AND device IS NULL\n"
                                               + "AND sample_tms > ?\n"
                                               + "GROUP BY sample_tms ORDER BY sample_tms";

    protected static final String SELECT_NET = "SELECT sample_tms,\n"
                                               + "MAX(CASE WHEN probe_type = 'net_tx' THEN sample_value ELSE NULL END) net_tx,\n"
                                               + "MAX(CASE WHEN probe_type = 'net_rx' THEN sample_value ELSE NULL END) net_rx\n"
                                               + "FROM tb_probe_series\n"
                                               + "WHERE hostname = ?\n"
                                               + "AND (probe_type = 'net_tx' OR probe_type = 'net_rx')\n"
                                               + "AND device = ?\n"
                                               + "AND sample_tms > ?\n"
                                               + "GROUP BY sample_tms ORDER BY sample_tms";

    protected static final String SELECT_DISK = "SELECT sample_tms,\n"
                                                + "MAX(CASE WHEN probe_type = 'disk_read' THEN sample_value ELSE NULL END) disk_read,\n"
                                                + "MAX(CASE WHEN probe_type = 'disk_write' THEN sample_value ELSE NULL END) disk_write\n"
                                                + "FROM tb_probe_series\n"
                                                + "WHERE hostname = ?\n"
                                                + "AND (probe_type = 'disk_read' OR probe_type = 'disk_write')\n"
                                                + "AND device = ?\n"
                                                + "AND sample_tms > ?\n"
                                                + "GROUP BY sample_tms ORDER BY sample_tms";

    protected static final String SELECT_GPU = "SELECT sample_tms, sample_value\n"
                                               + "FROM tb_probe_series\n"
                                               + "WHERE hostname = ?\n"
                                               + "AND probe_type = 'gpu'\n"
                                               + "AND device IS NULL\n"
                                               + "AND sample_tms > ?\n"
                                               + "ORDER BY sample_tms";

}
