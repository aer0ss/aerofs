package com.aerofs.lib.cfg;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.aerofs.base.C;
import com.aerofs.base.id.SID;
import com.aerofs.lib.Param;
import com.aerofs.lib.Versions;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDatabaseParams;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * This class contains all the configuration variables stored in the database.
 *
 * Variable values are stored as strings. When primitive types are used to set values, e.g. with
 * {@code set(Key, int)}, the class converts them to strings with "natural" conversions, e.g.
 * {@code Int.toString(int)}.
 *
 * Variable values are cached in memory for fast access.
 */
public class CfgDatabase
{
    public static enum Key
    {
        USER_ID("user_id", null),
        DEVICE_ID("device_id", null),
        CRED("cred", null),

        // Absolute path to Root Anchor. N.B. must not use canonical paths so users can use symbolic
        // links as root anchor and repoint to different paths later.
        ROOT("root", null),
        TIMEOUT("timeout", 45 * C.SEC),
        NOTIFY("notify", true),
        AUTO_EXPORT_FOLDER("autoexport_folder", null),

        // bind address for the Ritual socket. Use "*" to bind to all local addresses.
        RITUAL_BIND_ADDR("ritual_bind_addr", "127.0.0.1"),

        LAST_LOG_CLEANING("last_log_cleaning", 0),
        DAEMON_POST_UPDATES("daemon_post_updates", 0),
        UI_POST_UPDATES("ui_post_updates", 0),
        LAST_VER("last_ver", Versions.ZERO),

        // Hexadecimal checksum string that identifies the last version of the shell extension.
        // After an update, we compare against the current checksum to determine whether the
        // Shell Extension was updated.
        SHELLEXT_CHECKSUM("shellext_checksum", ""),

        // Config for command channel.
        TRANSIENT_CMD_CHANNEL_ID("cmd_channel_id", 0), // TODO (MP) eventually remove this.
        CMD_CHANNEL_ID("command_channel_id", 0),

        // Config for bandwidth throttling
        MAX_DOWN_RATE("max_down_rate", 0),  // 0 is unlimited
        MAX_UP_RATE("max_up_rate", 0),      // 0 is unlimited
        MAX_DOWN_RATE_LIMITED("max_down_rate_limited", 51200),
        MAX_UP_RATE_LIMITED("max_up_rate_limited", 10240),

        // Watermarks for IncomingStream chunks present in the core. Unit is chunk count.
        LOW_CHUNK_WATERMARK("low_chunk_watermark", 128),
        HIGH_CHUNK_WATERMARK("high_chunk_watermark", 640),

        // Config for Categories (see Cat.java)
        MAX_CLIENT_STACKS("max_client_stacks", 5),
        MAX_SERVER_STACKS("max_server_stacks", 20),
        MAX_HOUSEKEEPING_STACKS("max_housekeeping_stacks", 10),
        MAX_D2U_STACKS("max_d2u_stacks", 25),   // 25 = client + server stacks

        // Config for the TCP transport
        TCP_ENDPOINT("tcp_endpiont", null),
        // Used for port forwarding. valid only if 'tcp_endpoint' is present.
        TCP_INTERNAL_ENDPOINT("tcp_internal_endpoint", null),

        // Config for MySQL
        MYSQL_URL("mysql_url", null),
        MYSQL_LOGIN("mysql_login", null),
        MYSQL_PASSWD("mysql_passwd", null),

        // Config for S3
        S3_DIR("s3_dir", null),
        S3_ACCESS_KEY("s3_access_key", null),
        S3_SECRET_KEY("s3_secret_key", null),
        S3_BUCKET_ID("s3_bucket_id", null),
        // This field stores scrypt of the password provided by the user, salted by user id.
        // i.e. value = base64(scrypt(password|user))
        S3_ENCRYPTION_PASSWORD("s3_encryption_password", null),

        // Configs only recognizable by multiuser installs
        MULTIUSER_CONTACT_EMAIL("multiuser_contact_email", ""),

        // first start of the daemon
        FIRST_START("first_start", true),

        // storage type
        STORAGE_TYPE("storage_type", null)
        ;

        private final String _str;
        private @Nullable final String _defaultValue;

        /**
         * @param str the string representation of the key. Ideally it should be derived from
         * symbol names but not possible due to obfuscation. TODO use ProGuard annotations?
         */
        Key(String str, @Nullable String defaultValue)
        {
            _str = str;
            _defaultValue = defaultValue;
        }

        Key(String str, long defaultValue)
        {
            this(str, Long.toString(defaultValue));
        }

        Key(String str, boolean defaultValue)
        {
            this(str, Boolean.toString(defaultValue));
        }

        @Override
        public String toString()
        {
            return _str;
        }

        public String keyString()
        {
            return _str;
        }

        public String defaultValue()
        {
            return _defaultValue;
        }
    }

    private static class DatabaseParams implements IDatabaseParams
    {
        private final boolean _inMemory;

        DatabaseParams(boolean inMemory)
        {
            _inMemory = inMemory;
        }

        @Override
        public boolean isMySQL()
        {
            return false;
        }

        @Override
        public String url()
        {
            return "jdbc:sqlite:" + (_inMemory ? ":memory:" : Cfg.absRTRoot() + File.separator +
                    Param.CFG_DATABASE);
        }

        @Override
        public boolean sqliteExclusiveLocking()
        {
            return false;
        }

        @Override
        public boolean sqliteWALMode()
        {
            return false;
        }

        @Override
        public boolean autoCommit()
        {
            return true;
        }
    }

    private static final String T_CFG = "c";
    private static final String C_CFG_KEY = "k";
    private static final String C_CFG_VALUE = "v";

    private static final String T_ROOT = "r";
    private static final String C_ROOT_SID = "s";
    private static final String C_ROOT_PATH = "p";


    // all the variables are protected by synchronized (this)
    private IDBCW _dbcw;
    private final EnumMap<Key, String> _map = Maps.newEnumMap(Key.class);
    private final List<ICfgDatabaseListener> _listeners = Lists.newArrayList();
    private final boolean _inMemory;

    CfgDatabase()
    {
        this(false);
    }

    // TODO use DI to inject DBCW (similar to CoreDBCW)! It's not possible right now because UI
    // is not injectified
    CfgDatabase(boolean inMemory)
    {
        _inMemory = inMemory;
    }

    /**
     * Nop if this method has been called and succeeded before
     */
    void init_() throws SQLException
    {
        if (_dbcw != null) return;

        _dbcw = DBUtil.newDBCW(new DatabaseParams(_inMemory));
        _dbcw.init_();
    }

    /**
     * This object is not usable after this method is called, because we don't clean up prepared
     * statements which are no longer valid after their associated database connection is closed.
     */
    void fini_() throws SQLException
    {
        if (_dbcw == null) return;

        _dbcw.fini_();
        _dbcw = null;
    }

    public void recreateSchema_() throws SQLException
    {
        Statement s = _dbcw.getConnection().createStatement();
        try {
            s.executeUpdate("drop table if exists " + T_CFG);
            s.executeUpdate("create table " + T_CFG + "(" +
                    C_CFG_KEY + " text not null primary key," +
                    C_CFG_VALUE + " text not null) " + _dbcw.charSet());
            createRootTable_(s);
        } finally {
            s.close();
        }
    }

    public void createRootTable_(Statement enclosing) throws SQLException
    {
        Statement s = enclosing != null ? enclosing : _dbcw.getConnection().createStatement();
        try {
            s.executeUpdate("create table if not exists " + T_ROOT + "(" +
                    C_ROOT_SID + " blob not null primary key," +
                    C_ROOT_PATH + " text not null) " + _dbcw.charSet());
        } finally {
            if (s != enclosing) s.close();
        }
    }

    synchronized void reload() throws SQLException, ExNotSetup
    {
        Set<Key> oldKeys = Sets.newEnumSet(_map.keySet(), Key.class);
        _map.clear();

        Statement s = _dbcw.getConnection().createStatement();
        try {
            ResultSet rs = s.executeQuery(DBUtil.select(T_CFG, C_CFG_KEY, C_CFG_VALUE));
            try {
                while (rs.next()) {
                    String str = rs.getString(1);
                    for (Key key : Key.values()) {
                        if (key._str.equals(str)) {
                            _map.put(key, rs.getString(2));
                        }
                        // ignore un-recognized keys
                    }
                }
            } finally {
                rs.close();
            }

            if (!_map.containsKey(Key.DEVICE_ID)) throw new ExNotSetup();

            oldKeys.addAll(_map.keySet());
            for (Key key : oldKeys) notifyValueChanged_(key);

        } catch (SQLException e) {
            // it's a hacky way but the only way
            if (e.getMessage().contains("(no such table: " + T_CFG + ")")) throw new ExNotSetup();
            else throw e;
        } finally {
            s.close();
        }
    }

    public synchronized Map<SID, String> getRoots() throws SQLException
    {
        Statement s = _dbcw.getConnection().createStatement();
        try {
            ResultSet rs = s.executeQuery(DBUtil.select(T_ROOT, C_ROOT_SID, C_ROOT_PATH));
            try {
                Map<SID, String> roots = Maps.newHashMap();
                while (rs.next()) roots.put(new SID(rs.getBytes(1)), rs.getString(2));
                return roots;
            } finally {
                rs.close();
            }
        } finally {
            s.close();
        }
    }

    public synchronized void addRoot(SID sid, String absPath) throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement(
                DBUtil.insert(T_ROOT, C_ROOT_SID, C_ROOT_PATH));
        try {
            ps.setBytes(1, sid.getBytes());
            ps.setString(2, absPath);
            ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    public synchronized void removeRoot(SID sid) throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement(
                DBUtil.deleteWhere(T_ROOT, C_ROOT_SID + "=?"));
        try {
            ps.setBytes(1, sid.getBytes());
            ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    public synchronized void moveRoot(SID sid, String newAbsPath) throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement(
                DBUtil.updateWhere(T_ROOT, C_ROOT_SID + "=?", C_ROOT_PATH));
        try {
            ps.setString(1, newAbsPath);
            ps.setBytes(2, sid.getBytes());
            ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    public synchronized void addListener(ICfgDatabaseListener l)
    {
        _listeners.add(l);
    }

    private void notifyValueChanged_(Key key)
    {
        for (ICfgDatabaseListener l : _listeners) l.valueChanged_(key);
    }

    public void set(Key key, long value) throws SQLException
    {
        set(key, Long.toString(value));
    }

    public void set(Key key, int value) throws SQLException
    {
        set(key, Integer.toString(value));
    }

    public void set(Key key, boolean value) throws SQLException
    {
        set(key, Boolean.toString(value));
    }

    public synchronized void set(Key key, String value) throws SQLException
    {
        set(Collections.singletonMap(key, value));
    }

    private PreparedStatement _psSet;
    private PreparedStatement _psRemove;
    public synchronized void set(Map<Key, String> map) throws SQLException
    {
        try {
            if (_psSet == null) _psSet = _dbcw.getConnection()
                    .prepareStatement("replace into " + T_CFG + "(" + C_CFG_KEY + "," +
                            C_CFG_VALUE + ") values (?,?)");
            if (_psRemove == null) _psRemove = _dbcw.getConnection()
                    .prepareStatement("delete from " + T_CFG + " where " + C_CFG_KEY + "=?");

            boolean hasSet = false;
            boolean hasRemove = false;
            for (Entry<Key, String> en : map.entrySet()) {
                Key key = en.getKey();
                String value = en.getValue();
                assert value != null || key._defaultValue == null;
                if (value == null || value.equals(key._defaultValue)) {
                    _psRemove.setString(1, key._str);
                    _psRemove.addBatch();
                    // TODO rollback the map if transaction is aborted
                    _map.remove(key);
                    hasRemove = true;
                } else {
                    _psSet.setString(1, key._str);
                    _psSet.setString(2, value);
                    _psSet.addBatch();
                    // TODO rollback the map if transaction is aborted
                    _map.put(key, value);
                    hasSet = true;
                }
            }

            if (hasSet) _psSet.executeBatch();
            if (hasRemove) _psRemove.executeBatch();

            for (Key key : map.keySet()) notifyValueChanged_(key);

        } catch (SQLException e) {
            DBUtil.close(_psSet);
            DBUtil.close(_psRemove);
            _psSet = null;
            _psRemove = null;
            throw e;
        }
    }

    /**
     * @return the default value if the value is not set or the method is called before loadAll_().
     * The default value may be null.
     */
    public synchronized @Nullable String getNullable(Key key)
    {
        String v = _map.get(key);
        return v == null ? key._defaultValue : v;
    }

    /**
     * @pre the value is set, or the default value is not null
     */
    public @Nonnull String get(Key key)
    {
        String v = getNullable(key);
        assert v != null;
        return v;
    }


    /**
     * @pre the value must be set, or the default value is not null
     *
     * N.B frequently calling this method is inefficient. Consider caching the result
     */
    public int getInt(Key key)
    {
        return Integer.parseInt(get(key));
    }

    /**
     * @pre the value must be set, or the default value is not null
     *
     * N.B frequently calling this method is inefficient. Consider caching the result
     */
    public long getLong(Key key)
    {
        return Long.parseLong(get(key));
    }

    /**
     * @pre the value must be set, or the default value is not null
     *
     * N.B frequently calling this method is inefficient. Consider caching the result
     */
    public boolean getBoolean(Key key)
    {
        return Boolean.parseBoolean(get(key));
    }
}
