/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import com.aerofs.base.Loggers;
import com.aerofs.ids.SID;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.db.dbcw.SQLiteDBCW;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The conf DB contains a table that holds the mapping of SID->abs path for all external roots
 *
 * This class manages that table.
 */
public class RootDatabase
{
    private static final Logger l = Loggers.getLogger(RootDatabase.class);

    private final IDBCW _dbcw;

    private static final String T_ROOT = "r";
    private static final String C_ROOT_SID = "s";
    private static final String C_ROOT_PATH = "p";

    // replica used for clean reinstall
    // the canonical copy of the roots table resides in the conf db
    private static final String SEED_FILE = ".aerofs.roots";

    RootDatabase(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    public void createSeed_() throws SQLException
    {
        RootDatabase copy = new RootDatabase(seedDBCW());
        copy.recreateSchema_();
        for (Entry<SID, String> e : getRoots().entrySet()) {
            copy.addRoot(e.getKey(), e.getValue());
        }
        copy._dbcw.getConnection().commit();
        copy._dbcw.fini_();
    }

    public static Map<SID, String> loadSeed_() throws SQLException
    {
        if (!new File(Util.join(Cfg.absRTRoot(), SEED_FILE)).exists()) {
            return Collections.emptyMap();
        }
        RootDatabase rdb = new RootDatabase(seedDBCW());
        try {
            return rdb.getRoots();
        } finally {
            rdb._dbcw.fini_();
        }
    }

    public static void cleaupSeed_()
    {
        new File(Util.join(Cfg.absRTRoot(), SEED_FILE)).delete();
    }

    private static IDBCW seedDBCW() throws SQLException
    {
        IDBCW dbcw = new SQLiteDBCW("jdbc:sqlite:" + Util.join(Cfg.absRTRoot(), SEED_FILE),
                false, true, true);
        dbcw.init_();
        return dbcw;
    }

    void recreateSchema_() throws SQLException
    {
        Statement s = _dbcw.getConnection().createStatement();
        try {
            s.executeUpdate("drop table if exists " + T_ROOT);
            createRootTableIfAbsent_(s);
        } finally {
            s.close();
        }
    }

    void createRootTableIfAbsent_(Statement enclosing) throws SQLException
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

    // we need to pass canonical path to the Linker
    private String canonicalize(String path)
    {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            l.warn("ignored exception", e);
            return path;
        }
    }

    synchronized Map<SID, String> getRoots() throws SQLException
    {
        Statement s = _dbcw.getConnection().createStatement();
        try {
            ResultSet rs = s.executeQuery(DBUtil.select(T_ROOT, C_ROOT_SID, C_ROOT_PATH));
            try {
                Map<SID, String> roots = Maps.newHashMap();
                while (rs.next()) {
                    roots.put(new SID(rs.getBytes(1)), canonicalize(rs.getString(2)));
                }
                return roots;
            } finally {
                rs.close();
            }
        } finally {
            s.close();
        }
    }

    /**
     * @return the absolute path to which a given SID is linked
     *
     * Returns null if the given SID is not a valid root.
     *
     * NB: the returned value is canonicalized and may thus differ from the value
     * actually stored in the DB.
     *
     * TODO: ww says canonicalization can be expensive, check whether that's true and if so
     * move that to the place/time that actually require it (Linker/Notifier)
     */
    synchronized @Nullable String getRootNullable(SID sid) throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement(DBUtil.selectWhere(T_ROOT,
                C_ROOT_SID + "=?", C_ROOT_PATH));
        try {
            ps.setBytes(1, sid.getBytes());
            ResultSet rs = ps.executeQuery();
            try {
                return rs.next() ? canonicalize(rs.getString(1)) : null;
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }
    }

    synchronized void addRoot(SID sid, String absPath) throws SQLException
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

    // NB: public only for use in Setup.java
    synchronized void removeRoot(SID sid) throws SQLException
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

    synchronized void moveRoot(SID sid, String newAbsPath) throws SQLException
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
}
