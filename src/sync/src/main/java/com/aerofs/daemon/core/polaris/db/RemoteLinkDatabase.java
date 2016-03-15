/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.daemon.core.store.IStoreDeletionOperator;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import static com.aerofs.daemon.core.polaris.db.PolarisSchema.*;
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
    public RemoteLinkDatabase(IDBCW dbcw, StoreDeletionOperators sdo)
    {
        super(dbcw);
        sdo.addImmediate_(this);
    }

    private class Waiter extends AbstractFuture<RemoteLink> {
        private final OID oid;

        Waiter(OID oid) { this.oid = oid; }

        @Override
        public boolean set(@Nullable RemoteLink value) {
            return super.set(value);
        }

        @Override
        public boolean setException(Throwable t) {
            return super.setException(t);
        }

        @Override
        public boolean cancel(boolean maybeInterruptIfRunning) {
            _waiters.remove(oid, this);
            return super.cancel(maybeInterruptIfRunning);
        }
    }

    private final Map<OID, Waiter> _waiters = new ConcurrentHashMap<>();

    /**
     * Sometimes some code wants to make a direct blocking request to Polaris, e.g. when
     * sharing a folder, in which case it is important to ensure that the object in question
     * is present on polaris
     */
    public Future<RemoteLink> wait_(SIndex sidx, OID oid) throws SQLException {
        Waiter f = new Waiter(oid);
        RemoteLink lnk = getParent_(sidx, oid);
        if (lnk != null) {
            f.set(lnk);
        } else {
            Waiter prev = _waiters.putIfAbsent(oid, f);
            if (prev != null) f = prev;
        }
        return f;
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
            return "{" + Joiner.on(',').join(parent.toStringFormal(), name, logicalTimestamp) + "}";
        }
    }

    private final PreparedStatementWrapper _pswGet = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_REMOTE_LINK,
                    C_REMOTE_LINK_SIDX + "=? and " + C_REMOTE_LINK_OID + "=?",
                    C_REMOTE_LINK_PARENT, C_REMOTE_LINK_NAME, C_REMOTE_LINK_VERSION));
    public RemoteLink getParent_(SIndex sidx, OID oid) throws SQLException
    {
        try (ResultSet rs = query(_pswGet, sidx.getInt(), oid.getBytes())) {
            return !rs.next() ? null
                    : new RemoteLink(new OID(rs.getBytes(1)), rs.getString(2), rs.getLong(3));
        }
    }

    public static class RemoteChild {
        public final OID oid;
        public final String name;

        RemoteChild(OID oid, String name) {
            this.oid = oid;
            this.name = name;
        }
    }

    private final PreparedStatementWrapper _pswList = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_REMOTE_LINK,
                    C_REMOTE_LINK_SIDX + "=? and " + C_REMOTE_LINK_PARENT + "=?",
                    C_REMOTE_LINK_OID, C_REMOTE_LINK_NAME));
    public IDBIterator<RemoteChild> listChildren_(SIndex sidx, OID oid) throws SQLException {
        return new AbstractDBIterator<RemoteChild>(query(_pswList, sidx.getInt(), oid.getBytes())) {
            @Override
            public RemoteChild get_() throws SQLException {
                return new RemoteChild(new OID(_rs.getBytes(1)), _rs.getString(2));
            }
        };
    }

    private final PreparedStatementWrapper _pswInsert = new PreparedStatementWrapper(
            DBUtil.insert(T_REMOTE_LINK, C_REMOTE_LINK_SIDX, C_REMOTE_LINK_OID,
                    C_REMOTE_LINK_PARENT, C_REMOTE_LINK_NAME, C_REMOTE_LINK_VERSION));
    public void insertParent_(SIndex sidx, OID oid, OID parent, String name, long logicalTimestamp,
            Trans t)
            throws SQLException
    {
        checkState(1 == update(_pswInsert, sidx.getInt(), oid.getBytes(), parent.getBytes(), name,
                logicalTimestamp));
        Waiter f = _waiters.get(oid);
        if (f != null) {
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_() {
                    _waiters.remove(oid, f);
                    f.set(new RemoteLink(parent, name, logicalTimestamp));
                }
            });
        }
    }

    private final PreparedStatementWrapper _pswDelete = new PreparedStatementWrapper(
            DBUtil.deleteWhere(T_REMOTE_LINK,
                    C_REMOTE_LINK_SIDX + "=? and " + C_REMOTE_LINK_OID + "=?"));
    public void removeParent_(SIndex sidx, OID oid, Trans t) throws SQLException
    {
        checkState(1 == update(_pswDelete, sidx.getInt(), oid.getBytes()));
    }

    private final PreparedStatementWrapper _pswUpdate = new PreparedStatementWrapper(
            DBUtil.updateWhere(T_REMOTE_LINK,
                    C_REMOTE_LINK_SIDX + "=? and " + C_REMOTE_LINK_OID + "=?",
                    C_REMOTE_LINK_PARENT, C_REMOTE_LINK_NAME, C_REMOTE_LINK_VERSION));
    public void updateParent_(SIndex sidx, OID oid, OID parent, String name, long logicalTimestamp,
            Trans t)
            throws SQLException
    {
        checkState(1 == update(_pswUpdate,
                parent.getBytes(), name, logicalTimestamp, sidx.getInt(), oid.getBytes()));
    }

    private final PreparedStatementWrapper _pswHasChildren = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_REMOTE_LINK,
                    C_REMOTE_LINK_SIDX + "=? and " + C_REMOTE_LINK_PARENT + "=?" + " limit 1",
                    C_REMOTE_LINK_OID));
    public boolean hasChildren_(SIndex sidx, OID oid) throws SQLException
    {
        try (ResultSet rs = query(_pswHasChildren, sidx.getInt(), oid.getBytes())) {
            return rs.next();
        }
    }
}
