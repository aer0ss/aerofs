/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.first_launch;

import com.aerofs.base.Loggers;
import com.aerofs.ids.OID;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.SQLiteDBCW;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A seed database is a small sqlite db that can be generated from one device and passed
 * to another device at install time to speedup "seeding resolution" by avoiding as much
 * aliasing as possible.
 *
 * This DB only contains a simple (path, {FILE,DIR}) -> OID mapping and therefore will not
 * prevent false conflicts.
 *
 * A possible next step would be to keep version vectors as well, to prevent such false
 * conflicts but that would require a lot of care to correctly handle corner cases.
 *
 * This class has two usages:
 *
 * 1. restore OIDs on first launch:
 *    SeedDatabase db = SeedDatabase.load_()
 *    if db != null then
 *       ...
 *       db.getOID_()
 *       ...
 *       db.cleanup_()
 *
 * 2. populate a seed file (on unlink or at user request)
 *    SeedDatabase db = SeedDatabase.create_()
 *    ...
 *    db.setOID_()
 *    ...
 *    db.save_()
 */
public class SeedDatabase extends AbstractDatabase
{
    private static final Logger l = Loggers.getLogger(SeedDatabase.class);

    private static final String
            T_SEED      = "s",
            C_SEED_PATH = "s_p",
            C_SEED_TYPE = "s_t",
            C_SEED_OID  = "s_o";

    private final String _path;

    private SeedDatabase(String path)
    {
        super(new SQLiteDBCW("jdbc:sqlite:" + path, false, true, true));
        _path = path;
    }

    static @Nullable SeedDatabase load_(String path)
    {
        if (!new File(path).exists()) {
            return null;
        }

        l.info("seed file found {}", path);
        SeedDatabase db = new SeedDatabase(path);
        try {
            db._dbcw.init_();
            if (db._dbcw.tableExists(T_SEED)) return db;
        } catch (SQLException e) {
            l.info("failed to load seed file", e);
            db.cleanup_();
        }
        return null;
    }

    private PreparedStatement _psGetOID;
    @Nullable OID getOID_(Path path, boolean dir) throws SQLException
    {
        try {
            if (_psGetOID == null) {
                _psGetOID = c().prepareStatement(DBUtil.selectWhere(T_SEED,
                        C_SEED_PATH + "=? and " + C_SEED_TYPE + "=?", C_SEED_OID));
            }

            _psGetOID.setString(1, path.toStringRelative());
            _psGetOID.setInt(2, dir ? 1 : 0);

            ResultSet rs = _psGetOID.executeQuery();
            return rs.next() ? new OID(rs.getBytes(1)) : null;
        } catch (SQLException e) {
            DBUtil.close(_psGetOID);
            _psGetOID = null;
            throw detectCorruption(e);
        }
    }

    void cleanup_()
    {
        l.info("cleanup seed file {}", _path);
        try {
            _dbcw.fini_();
        } catch (SQLException e) {
            // can safely ignore
        }
        new File(_path).delete();
    }

    /**
     * setup schema prior to populating the db
     */
    static SeedDatabase create_(String path) throws SQLException
    {
        SeedDatabase db = new SeedDatabase(path);
        try {
            db._dbcw.init_();
            Statement s = db.c().createStatement();
            try {
                s.executeUpdate("create table " + T_SEED + "("
                        + C_SEED_PATH + " text not null,"
                        + C_SEED_TYPE + " integer not null,"
                        + C_SEED_OID + " blob not null,"
                        + " primary key (" + C_SEED_PATH + "," + C_SEED_TYPE + ")"
                        + ") ");
            } finally {
                s.close();
            }
        } catch (SQLException e) {
            db.cleanup_();
            throw e;
        }
        return db;
    }

    private PreparedStatement _psSetOID;
    void setOID_(String path, boolean dir, OID oid) throws SQLException
    {
        try {
            if (_psSetOID == null) {
                _psSetOID = c().prepareStatement(DBUtil.insert(T_SEED,
                        C_SEED_PATH, C_SEED_TYPE, C_SEED_OID));
            }

            _psSetOID.setString(1, path);
            _psSetOID.setInt(2, dir ? 1 : 0);
            _psSetOID.setBytes(3, oid.getBytes());

            int n = _psSetOID.executeUpdate();
            Util.verify(n == 1);
        } catch (SQLException e) {
            DBUtil.close(_psSetOID);
            _psSetOID = null;
            throw detectCorruption(e);
        }
    }

    String save_() throws SQLException
    {
        c().commit();
        _dbcw.fini_();
        return _path;
    }
}
