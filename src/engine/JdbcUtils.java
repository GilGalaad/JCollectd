package engine;

public class JdbcUtils {

    protected static final String CREATE_TB_STMT = "CREATE TABLE IF NOT EXISTS probe_samples (id_sample INTEGER PRIMARY KEY, probe_name TEXT, sample_tms TEXT, sample_value TEXT)";
    protected static final String CREATE_IDX_STMT = "CREATE UNIQUE INDEX IF NOT EXISTS idx_probe_samples ON probe_samples (probe_name, sample_tms)";
    protected static final String INS_STMT = "INSERT INTO probe_samples (probe_name, sample_tms, sample_value) VALUES (?,?,?)";
    protected static final String DEL_STMT = "DELETE FROM probe_samples WHERE sample_tms < ?";
    protected static final String BEGIN_TRANS = "BEGIN TRANSACTION";
    protected static final String END_TRANS = "END TRANSACTION";
    protected static final String ANALYZE = "ANALYZE";
    protected static final String VACUUM = "VACUUM";
    protected static final String PRAGMA = "PRAGMA busy_timeout = 5000";

    protected static final String SELECT_LOAD = "SELECT sample_tms,\n"
            + "MAX(CASE WHEN probe_name = 'load1m' THEN sample_value ELSE NULL END) l1m,\n"
            + "MAX(CASE WHEN probe_name = 'load5m' THEN sample_value ELSE NULL END) l5m,\n"
            + "MAX(CASE WHEN probe_name = 'load15m' THEN sample_value ELSE NULL END) l15m\n"
            + "FROM probe_samples\n"
            + "WHERE sample_tms > ?\n"
            + "AND (probe_name = 'load1m' OR probe_name = 'load5m' OR probe_name = 'load15m')\n"
            + "GROUP BY sample_tms ORDER BY sample_tms;";

    protected static final String SELECT_CPU = "SELECT sample_tms, sample_value\n"
            + "FROM probe_samples\n"
            + "WHERE sample_tms > ?\n"
            + "AND probe_name = 'cpu'\n"
            + "ORDER BY sample_tms;";

    protected static final String SELECT_MEM = "SELECT sample_tms,\n"
            + "MAX(CASE WHEN probe_name = 'mem' THEN sample_value ELSE NULL END) mem,\n"
            + "MAX(CASE WHEN probe_name = 'swap' THEN sample_value ELSE NULL END) swap\n"
            + "FROM probe_samples\n"
            + "WHERE sample_tms > ?\n"
            + "AND (probe_name = 'mem' OR probe_name = 'swap')\n"
            + "GROUP BY sample_tms ORDER BY sample_tms";

    protected static final String SELECT_NET = "SELECT sample_tms,\n"
            + "MAX(CASE WHEN probe_name LIKE '%tx%' THEN sample_value ELSE NULL END) tx,\n"
            + "MAX(CASE WHEN probe_name LIKE '%rx%' THEN sample_value ELSE NULL END) rx\n"
            + "FROM probe_samples\n"
            + "WHERE sample_tms > ?\n"
            + "AND (probe_name = ? OR probe_name = ?)\n"
            + "GROUP BY sample_tms ORDER BY sample_tms";

    protected static final String SELECT_HDD = "SELECT sample_tms,\n"
            + "MAX(CASE WHEN probe_name LIKE '%read%' THEN sample_value ELSE NULL END) read,\n"
            + "MAX(CASE WHEN probe_name LIKE '%write%' THEN sample_value ELSE NULL END) write\n"
            + "FROM probe_samples\n"
            + "WHERE sample_tms > ?\n"
            + "AND (probe_name = ? OR probe_name = ?)\n"
            + "GROUP BY sample_tms ORDER BY sample_tms";

}
