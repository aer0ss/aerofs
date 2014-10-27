/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.store.IStoreDeletionOperator;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.id.SIndex;
import com.google.common.base.Joiner;
import com.google.inject.Inject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 * Keep track of parent<->child relationships on Polaris
 *
 * The OA table maintains parent<->child relationships but it reflects the latest
 * local state, which will differ from the accepted central state until local changes
 * are submitted.
 *
 * Submission of MOVE operations require the device to keep track of the latest known
 * parent for every object. This could be stored directly in the meta changes table but
 * that would make it harder to update upon reception of remote changes.
 *
 * This table is updated whenever:
 *  - a remote change is received
 *  - a local change is successfully submitted
 *
 *  NB: this means, among other things that the (parent, name) pairs are NOT guaranteed to
 *  be unique as concurrent chains of renames can result in intermediate remote changes that
 *  temporarily conflict with accepted local changes.
 *
 *  NB: this object tree can differ from the local tree in two ways:
 *   1. it will not reflect local changes not yet submitted to polaris
 *   2. it may reflect remote changes received from polaris but not yet applied locally
 *
 * See
 * {@link com.aerofs.daemon.core.polaris.db.MetaChangesDatabase}
 * {@link com.aerofs.daemon.core.polaris.db.MetaBufferDatabase}
 */
public class RemoteLinkDatabase extends AbstractDatabase implements IStoreDeletionOperator
{
    @Inject
    public RemoteLinkDatabase(CoreDBCW dbcw, StoreDeletionOperators sdo)
    {
        super(dbcw.get());
        sdo.addImmediate_(this);
    }

    @Override
    public void deleteStore_(SIndex sidx, Trans t) throws SQLException
    {
        if (!_dbcw.tableExists(T_REMOTE_LINK)) return;
        try (Statement s = c().createStatement()) {
            s.executeUpdate("delete from " + T_REMOTE_LINK + " where " + C_REMOTE_LINK_SIDX + "=" +
                    sidx.getInt());
        }
    }

    public static class RemoteLink
    {
        public final OID parent;
        public final String name;
        public final long logicalTimestamp;

        public RemoteLink(OID parent, String name, long logicalTimestamp)
        {
            this.parent = parent;
            this.name = name;
            this.logicalTimestamp = logicalTimestamp;
        }

        @Override
        public boolean equals(Object o)
        {
            return o != null && o instanceof RemoteLink
                    && parent.equals(((RemoteLink)o).parent)
                    && name.equals(((RemoteLink)o).name)
                    && logicalTimestamp == ((RemoteLink)o).logicalTimestamp;
        }

        @Override
        public int hashCode()
        {
            return parent.hashCode() ^ name.hashCode();
        }

        @Override
        public String toString()
        {
            return "{" + Joiner.on(',').join(parent, name, logicalTimestamp) + "}";
        }
    }

    private final PreparedStatementWrapper _pswGet = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_REMOTE_LINK,
                    C_REMOTE_LINK_SIDX + "=? and " + C_REMOTE_LINK_OID + "=?",
                    C_REMOTE_LINK_PARENT, C_REMOTE_LINK_NAME, C_REMOTE_LINK_VERSION));
    public RemoteLink getParent_(SIndex sidx, OID oid) throws SQLException
    {
        return exec(_pswGet, ps -> {
            ps.setInt(1, sidx.getInt());
            ps.setBytes(2, oid.getBytes());
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next() ? null
                        : new RemoteLink(new OID(rs.getBytes(1)), rs.getString(2), rs.getLong(3));
            }
        });
    }

    private final PreparedStatementWrapper _pswInsert = new PreparedStatementWrapper(
            DBUtil.insert(T_REMOTE_LINK, C_REMOTE_LINK_SIDX, C_REMOTE_LINK_OID,
                    C_REMOTE_LINK_PARENT, C_REMOTE_LINK_NAME, C_REMOTE_LINK_VERSION));
    public void insertParent_(SIndex sidx, OID oid, OID parent, String name, long logicalTimestamp,
            Trans t)
            throws SQLException
    {
        exec(_pswInsert, ps -> {
            ps.setInt(1, sidx.getInt());
            ps.setBytes(2, oid.getBytes());
            ps.setBytes(3, parent.getBytes());
            ps.setString(4, name);
            ps.setLong(5, logicalTimestamp);
            checkState(ps.executeUpdate() == 1);
            return null;
        });
    }

    private final PreparedStatementWrapper _pswDelete = new PreparedStatementWrapper(
            DBUtil.deleteWhere(T_REMOTE_LINK,
                    C_REMOTE_LINK_SIDX + "=? and " + C_REMOTE_LINK_OID + "=?"));
    public void removeParent_(SIndex sidx, OID oid, Trans t) throws SQLException
    {
        exec(_pswDelete, ps -> {
            ps.setInt(1, sidx.getInt());
            ps.setBytes(2, oid.getBytes());
            checkState(ps.executeUpdate() == 1);
            return null;
        });
    }

    private final PreparedStatementWrapper _pswUpdate = new PreparedStatementWrapper(
            DBUtil.updateWhere(T_REMOTE_LINK,
                    C_REMOTE_LINK_SIDX + "=? and " + C_REMOTE_LINK_OID + "=?",
                    C_REMOTE_LINK_PARENT, C_REMOTE_LINK_NAME, C_REMOTE_LINK_VERSION));
    public void updateParent_(SIndex sidx, OID oid, OID parent, String name, long logicalTimestamp,
            Trans t)
            throws SQLException
    {
        exec(_pswUpdate, ps -> {
            ps.setBytes(1, parent.getBytes());
            ps.setString(2, name);
            ps.setLong(3, logicalTimestamp);
            ps.setInt(4, sidx.getInt());
            ps.setBytes(5, oid.getBytes());
            checkState(ps.executeUpdate() == 1);
            return null;
        });
    }
}
