/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.ids.OID;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.store.IStoreDeletionOperator;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.AbstractDatabase;
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
import java.sql.Statement;

import static com.aerofs.daemon.core.polaris.db.PolarisSchema.*;
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
public class MetaBufferDatabase extends AbstractDatabase implements IStoreDeletionOperator
{
    @Inject
    public MetaBufferDatabase(IDBCW dbcw, StoreDeletionOperators sdo)
    {
        super(dbcw);
        sdo.addImmediate_(this);
    }

    @Override
    public void deleteStore_(SIndex sidx, Trans t) throws SQLException
    {
        if (!_dbcw.tableExists(T_META_BUFFER)) return;
        try (Statement s = c().createStatement()) {
            s.executeUpdate("delete from " + T_META_BUFFER
                    + " where " + C_META_BUFFER_SIDX + "=" + sidx.getInt());
        }
    }

    private final PreparedStatementWrapper _pswInsert = new PreparedStatementWrapper(
            DBUtil.insert(T_META_BUFFER,
                    C_META_BUFFER_SIDX, C_META_BUFFER_OID, C_META_BUFFER_TYPE, C_META_BUFFER_MIGRANT, C_META_BUFFER_BOUND));
    public void insert_(SIndex sidx, OID oid, Type type, @Nullable OID migrant, long mergeBoundary,
                        Trans t)
            throws SQLException
    {
        checkState(1 == update(_pswInsert, sidx.getInt(), oid.getBytes(), type.ordinal(),
                migrant != null ? migrant.getBytes() : null, mergeBoundary));
    }

    public static class BufferedChange
    {
        public final OID oid;
        public final Type type;
        public final @Nullable OID migrant;

        public BufferedChange(OID oid, Type type, @Nullable OID migrant)
        {
            this.oid = oid;
            this.type = type;
            this.migrant = migrant;
        }

        @Override
        public String toString() {
            return "{" + oid + "," + type + "," + migrant + "}";
        }
    }

    private final PreparedStatementWrapper _pswList = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_META_BUFFER,
                    C_META_BUFFER_SIDX + "=? and " + C_META_BUFFER_BOUND + "<=?",
                    C_META_BUFFER_OID, C_META_BUFFER_TYPE, C_META_BUFFER_MIGRANT));
    public @Nullable BufferedChange getBufferedChange_(SIndex sidx, long until)
            throws SQLException
    {
        try (ResultSet rs = query(_pswList, sidx.getInt(), until)) {
            if (!rs.next()) return null;
            byte[] migrant = rs.getBytes(3);
            return new BufferedChange(new OID(rs.getBytes(1)), Type.valueOf(rs.getInt(2)),
                    migrant != null ? new OID(migrant) : null);
        }
    }

    private final PreparedStatementWrapper _pswGet = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_META_BUFFER,
                    C_META_BUFFER_SIDX + "=? and " + C_META_BUFFER_OID + "=?",
                    C_META_BUFFER_TYPE, C_META_BUFFER_MIGRANT));
    public @Nullable BufferedChange getBufferedChange_(SIndex sidx, OID oid)
            throws SQLException
    {
        try (ResultSet rs = query(_pswGet, sidx.getInt(), oid.getBytes())) {
            if (!rs.next()) return null;
            byte[] migrant = rs.getBytes(2);
            return new BufferedChange(oid, Type.valueOf(rs.getInt(1)),
                    migrant != null ? new OID(migrant) : null);
        }
    }

    private final PreparedStatementWrapper _pswDelete = new PreparedStatementWrapper(
            DBUtil.deleteWhere(T_META_BUFFER,
                    C_META_BUFFER_SIDX + "=? and " + C_META_BUFFER_OID + "=?"));
    public boolean remove_(SIndex sidx, OID oid, Trans t) throws SQLException
    {
        return 1 == update(_pswDelete, sidx.getInt(), oid.getBytes());
    }

    private final PreparedStatementWrapper _pswExists = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_META_BUFFER,
                    C_META_BUFFER_SIDX + "=? and " + C_META_BUFFER_OID + "=?",
                    C_META_BUFFER_BOUND));
    public boolean isBuffered_(SOID soid) throws SQLException
    {
        try (ResultSet rs = query(_pswExists, soid.sidx().getInt(), soid.oid().getBytes())) {
            return rs.next();
        }
    }
}
