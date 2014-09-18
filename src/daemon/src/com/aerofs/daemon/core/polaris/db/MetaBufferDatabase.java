/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 * This table keeps track of object with remote changes that have not yet been applied
 * to the local object tree. This buffering of change is used to more accurately resolve
 * name conflicts.
 *
 * Because Polaris does not send a minimal diff but instead a complete ordered diff, it is
 * possible for false conflicts to arise or conversely for true conflicts to only become
 * detectable much later than would be desirable.
 *
 * In particular we want to:
 *   1. prevent obsolete names from incorrectly causing aliasing
 *   2. prevent obsolete object names from incorrectly causing renaming
 *   3. avoid having to alias two local objects
 *
 * To that end, we always apply remote changes to {@link RemoteLinkDatabase} but sometimes
 * defer application of the change to the local tree until sufficient information has been
 * received from Polaris to unambiguously resolve false and true name conflicts.
 */
public class MetaBufferDatabase extends AbstractDatabase
{
    @Inject
    public MetaBufferDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private final PreparedStatementWrapper _pswInsert = new PreparedStatementWrapper(
            DBUtil.insert(T_META_BUFFER,
                    C_META_BUFFER_SIDX, C_META_BUFFER_OID, C_META_BUFFER_TYPE, C_META_BUFFER_BOUND));
    public void insert_(SIndex sidx, OID oid, Type type, long mergeBoundary, Trans t)
            throws SQLException
    {
        exec(_pswInsert, ps -> {
            ps.setInt(1, sidx.getInt());
            ps.setBytes(2, oid.getBytes());
            ps.setInt(3, type.ordinal());
            ps.setLong(4, mergeBoundary);
            checkState(ps.executeUpdate() == 1);
            return null;
        });
    }

    public static class BufferedChange
    {
        public final OID oid;
        public final Type type;

        public BufferedChange(OID oid, Type type)
        {
            this.oid = oid;
            this.type = type;
        }
    }

    private final PreparedStatementWrapper _pswList = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_META_BUFFER,
                    C_META_BUFFER_SIDX + "=? and " + C_META_BUFFER_BOUND + "<=?",
                    C_META_BUFFER_OID, C_META_BUFFER_TYPE));
    public @Nullable BufferedChange getBufferedChange_(SIndex sidx, long until)
            throws SQLException
    {
        return exec(_pswList, ps -> {
            ps.setInt(1, sidx.getInt());
            ps.setLong(2, until);
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next() ? null
                        : new BufferedChange(new OID(rs.getBytes(1)), Type.valueOf(rs.getInt(2)));
            }
        });
    }

    private final PreparedStatementWrapper _pswDelete = new PreparedStatementWrapper(
            DBUtil.deleteWhere(T_META_BUFFER,
                    C_META_BUFFER_SIDX + "=? and " + C_META_BUFFER_OID + "=?"));
    public boolean remove_(SIndex sidx, OID oid, Trans t) throws SQLException
    {
        return exec(_pswDelete, ps -> {
            ps.setLong(1, sidx.getInt());
            ps.setBytes(2, oid.getBytes());
            return ps.executeUpdate() == 1;
        });
    }

    private final PreparedStatementWrapper _pswGet = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_META_BUFFER,
                    C_META_BUFFER_SIDX + "=? and " + C_META_BUFFER_OID + "=?",
                    C_META_BUFFER_BOUND));
    public boolean isBuffered_(SOID soid) throws SQLException
    {
        return exec(_pswGet, ps -> {
            ps.setInt(1, soid.sidx().getInt());
            ps.setBytes(2, soid.oid().getBytes());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        });
    }
}
