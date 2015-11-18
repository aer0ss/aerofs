package com.aerofs.lib.cfg;

import com.aerofs.lib.Versions;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.aerofs.lib.cfg.CfgKey.getAllConfigKeys;

/**
 * This class contains all the configuration variables stored in the database.
 *
 * Variable values are stored as strings. When primitive types are used to set values, e.g. with
 * {@code set(Key, int)}, the class converts them to strings with "natural" conversions, e.g.
 * {@code Int.toString(int)}.
 *
 * Variable values are cached in memory for fast access.
 */
public class CfgDatabase extends ICfgStore
{

    private static final String T_CFG = "c";
    private static final String C_CFG_KEY = "k";
    private static final String C_CFG_VALUE = "v";

    public static final CfgKey NOTIFY = new CfgKey("notify", true);

    @Deprecated
    public static final CfgKey AUTO_EXPORT_FOLDER = new CfgKey("autoexport_folder", null);

    // bind address for the Ritual socket. Use "*" to bind to all local addresses.
    public static final CfgKey RITUAL_BIND_ADDR = new CfgKey("ritual_bind_addr", "127.0.0.1");

    public static final CfgKey LAST_LOG_CLEANING = new CfgKey("last_log_cleaning", 0);
    public static final CfgKey DAEMON_POST_UPDATES = new CfgKey("daemon_post_updates", 0);
    public static final CfgKey PHOENIX_CONVERSION = new CfgKey("phoenix_conversion", 0);
    public static final CfgKey UI_POST_UPDATES = new CfgKey("ui_post_updates", 0);
    public static final CfgKey LAST_VER = new CfgKey("last_ver", Versions.ZERO);

    // Hexadecimal checksum string that identifies the last version of the shell extension.
    // After an update, we compare against the current checksum to determine whether the
    // Shell Extension was updated.
    public static final CfgKey SHELLEXT_CHECKSUM = new CfgKey("shellext_checksum", "");

    // Config for bandwidth throttling
    public static final CfgKey MAX_DOWN_RATE = new CfgKey("max_down_rate", 0);  // 0 is unlimited
    public static final CfgKey MAX_UP_RATE = new CfgKey("max_up_rate", 0);      // 0 is unlimited
    public static final CfgKey MAX_DOWN_RATE_LIMITED = new CfgKey("max_down_rate_limited", 51200);
    public static final CfgKey MAX_UP_RATE_LIMITED = new CfgKey("max_up_rate_limited", 10240);

    // Watermarks for IncomingStream chunks present in the core. Unit is chunk count.
    public static final CfgKey LOW_CHUNK_WATERMARK = new CfgKey("low_chunk_watermark", 128);
    public static final CfgKey HIGH_CHUNK_WATERMARK = new CfgKey("high_chunk_watermark", 640);

    // Old name for the encryption password
    public static final CfgKey S3_LEGACY_ENCRYPTION_PASSWORD =
            new CfgKey("s3_encryption_password", null);

    // first start of the daemon
    public static final CfgKey FIRST_START = new CfgKey("first_start", true);


    // all the variables are protected by synchronized (this)
    private final IDBCW _dbcw;
    private final List<ICfgDatabaseListener> _listeners = Lists.newArrayList();

    CfgDatabase(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    public void recreateSchema_() throws SQLException
    {
        Statement s = _dbcw.getConnection().createStatement();
        try {
            s.executeUpdate("drop table if exists " + T_CFG);
            s.executeUpdate("create table " + T_CFG + "(" +
                    C_CFG_KEY + " text not null primary key," +
                    C_CFG_VALUE + " text not null) " + _dbcw.charSet());
        } finally {
            s.close();
        }
    }

    synchronized void reload() throws SQLException, ExNotSetup
    {
        Set<CfgKey> oldKeys = Sets.newHashSet(_map.keySet());
        _map.clear();

        Statement s = _dbcw.getConnection().createStatement();
        try {
            ResultSet rs = s.executeQuery(DBUtil.select(T_CFG, C_CFG_KEY, C_CFG_VALUE));
            try {
                while (rs.next()) {
                    String str = rs.getString(1);
                    for (CfgKey key : getAllConfigKeys()) {
                        if(key.keyString().equals(str)) {
                            _map.put(key, rs.getString(2));
                        }
                        // ignore un-recognized keys
                    }
                }
            } finally {
                rs.close();
            }

            if (!_map.containsKey(DEVICE_ID)) throw new ExNotSetup();

            oldKeys.addAll(_map.keySet());
            oldKeys.forEach(this::notifyValueChanged_);

        } catch (SQLException e) {
            // it's a hacky way but the only way
            if (e.getMessage().contains("(no such table: " + T_CFG + ")")) throw new ExNotSetup();
            else throw e;
        } finally {
            s.close();
        }
    }

    public synchronized void addListener(ICfgDatabaseListener l)
    {
        _listeners.add(l);
    }

    private void notifyValueChanged_(CfgKey key)
    {
        for (ICfgDatabaseListener l : _listeners) l.valueChanged_(key);
    }

    public void set(CfgKey key, long value) throws SQLException
    {
        set(key, Long.toString(value));
    }

    public void set(CfgKey key, int value) throws SQLException
    {
        set(key, Integer.toString(value));
    }

    public void set(CfgKey key, boolean value) throws SQLException
    {
        set(key, Boolean.toString(value));
    }

    public synchronized void set(CfgKey key, String value) throws SQLException
    {
        set(Collections.singletonMap(key, value));
    }

    private PreparedStatement _psSet;
    private PreparedStatement _psRemove;
    public synchronized void set(Map<CfgKey, String> map) throws SQLException
    {
        try {
            if (_psSet == null) _psSet = _dbcw.getConnection()
                    .prepareStatement("replace into " + T_CFG + "(" + C_CFG_KEY + "," +
                            C_CFG_VALUE + ") values (?,?)");
            if (_psRemove == null) _psRemove = _dbcw.getConnection()
                    .prepareStatement("delete from " + T_CFG + " where " + C_CFG_KEY + "=?");

            boolean hasSet = false;
            boolean hasRemove = false;
            for (Entry<CfgKey, String> en : map.entrySet()) {
                CfgKey key = en.getKey();
                String value = en.getValue();
                assert value != null || key.defaultValue() == null;
                if (value == null || value.equals(key.defaultValue())) {
                    _psRemove.setString(1, key.keyString());
                    _psRemove.addBatch();
                    // TODO rollback the map if transaction is aborted
                    _map.remove(key);
                    hasRemove = true;
                } else {
                    _psSet.setString(1, key.keyString());
                    _psSet.setString(2, value);
                    _psSet.addBatch();
                    // TODO rollback the map if transaction is aborted
                    _map.put(key, value);
                    hasSet = true;
                }
            }

            if (hasSet) _psSet.executeBatch();
            if (hasRemove) _psRemove.executeBatch();

            map.keySet().forEach(this::notifyValueChanged_);

        } catch (SQLException e) {
            DBUtil.close(_psSet);
            DBUtil.close(_psRemove);
            _psSet = null;
            _psRemove = null;
            throw e;
        }
    }
}
