/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 * Manages the database of centralized version numbers.
 *
 * In the before time a distributed version system was used which used vectors where
 * each contributing device had its own tick space. A the mighty Polaris descended upon
 * us, it became possible to adopt a much simpler scalar version number, where ticks
 * are issued by the central server.
 */
public class CentralVersionDatabase extends AbstractDatabase
{
    @Inject
    public CentralVersionDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private final PreparedStatementWrapper _pswGetVersion = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_VERSION,
                    C_VERSION_SIDX + "=? and " + C_VERSION_OID + "=?", C_VERSION_TICK));
    public @Nullable Long getVersion_(SIndex sidx, OID oid) throws SQLException
    {
        return exec(_pswGetVersion, ps -> {
            ps.setInt(1, sidx.getInt());
            ps.setBytes(2, oid.getBytes());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        });
    }

    private final PreparedStatementWrapper _pswSetVersion = new PreparedStatementWrapper(
            DBUtil.updateWhere(T_VERSION,
                    C_VERSION_SIDX + "=? and " + C_VERSION_OID + "=?", C_VERSION_TICK));
    private final PreparedStatementWrapper _pswInsertVersion = new PreparedStatementWrapper(
            DBUtil.insert(T_VERSION, C_VERSION_SIDX, C_VERSION_OID, C_VERSION_TICK));
    public void setVersion_(SIndex sidx, OID oid, long version, Trans t) throws SQLException
    {
        Loggers.getLogger(CentralVersionDatabase.class).info("set {} {} {}", sidx, oid, version);
        // FIXME: update count may not be trustworthy
        checkState(exec(_pswSetVersion, ps -> {
            ps.setLong(1, version);
            ps.setInt(2, sidx.getInt());
            ps.setBytes(3, oid.getBytes());
            return (ps.executeUpdate() == 1);
        }) || exec(_pswInsertVersion, ps -> {
            ps.setInt(1, sidx.getInt());
            ps.setBytes(2, oid.getBytes());
            ps.setLong(3, version);
            return (ps.executeUpdate() == 1);
        }));
    }
}
