package jcollectd.engine.db.sqlite;

public class SqliteUtils {

    private SqliteUtils() {
    }

    protected static final String CREATE_TB_STMT = """
            CREATE TABLE IF NOT EXISTS tb_probe_series (
            id_sample INTEGER PRIMARY KEY,
            hostname TEXT NOT NULL,
            probe_type TEXT NOT NULL,
            device TEXT,
            sample_tms TEXT NOT NULL,
            sample_value REAL NOT NULL
            )""";

    protected static final String CREATE_IDX_STMT = "CREATE UNIQUE INDEX IF NOT EXISTS idx_probe_series ON tb_probe_series (hostname, probe_type, device, sample_tms)";

    protected static final String INS_STMT = "INSERT INTO tb_probe_series (hostname, probe_type, device, sample_tms, sample_value) VALUES (?,?,?,?,?)";

    protected static final String DEL_STMT = """
            DELETE FROM tb_probe_series
            WHERE hostname = ?
            AND sample_tms <= ?""";

    protected static final String BEGIN_TRANS = "BEGIN TRANSACTION";

    protected static final String END_TRANS = "END TRANSACTION";

    protected static final String ANALYZE = "ANALYZE";

    protected static final String VACUUM = "VACUUM";

    protected static final String PRAGMA = "PRAGMA busy_timeout = 5000";

    protected static final String SELECT_LOAD = """
            SELECT sample_tms,
            MAX(CASE WHEN probe_type = 'load1m' THEN sample_value ELSE NULL END) load1m,
            MAX(CASE WHEN probe_type = 'load5m' THEN sample_value ELSE NULL END) load5m,
            MAX(CASE WHEN probe_type = 'load15m' THEN sample_value ELSE NULL END) load15m
            FROM tb_probe_series
            WHERE hostname = ?
            AND (probe_type = 'load1m' OR probe_type = 'load5m' OR probe_type = 'load15m')
            AND device IS NULL
            AND sample_tms > ?
            GROUP BY sample_tms ORDER BY sample_tms""";

    protected static final String SELECT_CPU = """
            SELECT sample_tms, sample_value
            FROM tb_probe_series
            WHERE hostname = ?
            AND probe_type = 'cpu'
            AND device IS NULL
            AND sample_tms > ?
            ORDER BY sample_tms""";

    protected static final String SELECT_MEM = """
            SELECT sample_tms,
            MAX(CASE WHEN probe_type = 'mem' THEN sample_value ELSE NULL END) mem,
            MAX(CASE WHEN probe_type = 'swap' THEN sample_value ELSE NULL END) swap,
            MAX(CASE WHEN probe_type = 'cache' THEN sample_value ELSE NULL END) cache
            FROM tb_probe_series
            WHERE hostname = ?
            AND (probe_type = 'mem' OR probe_type = 'swap' OR probe_type = 'cache')
            AND device IS NULL
            AND sample_tms > ?
            GROUP BY sample_tms ORDER BY sample_tms""";

    protected static final String SELECT_NET = """
            SELECT sample_tms,
            MAX(CASE WHEN probe_type = 'net_tx' THEN sample_value ELSE NULL END) net_tx,
            MAX(CASE WHEN probe_type = 'net_rx' THEN sample_value ELSE NULL END) net_rx
            FROM tb_probe_series
            WHERE hostname = ?
            AND (probe_type = 'net_tx' OR probe_type = 'net_rx')
            AND device = ?
            AND sample_tms > ?
            GROUP BY sample_tms ORDER BY sample_tms""";

    protected static final String SELECT_DISK = """
            SELECT sample_tms,
            MAX(CASE WHEN probe_type = 'disk_read' THEN sample_value ELSE NULL END) disk_read,
            MAX(CASE WHEN probe_type = 'disk_write' THEN sample_value ELSE NULL END) disk_write
            FROM tb_probe_series
            WHERE hostname = ?
            AND (probe_type = 'disk_read' OR probe_type = 'disk_write')
            AND device = ?
            AND sample_tms > ?
            GROUP BY sample_tms ORDER BY sample_tms""";

    protected static final String SELECT_GPU = """
            SELECT sample_tms, sample_value
            FROM tb_probe_series
            WHERE hostname = ?
            AND probe_type = 'gpu'
            AND device IS NULL
            AND sample_tms > ?
            ORDER BY sample_tms""";

}
