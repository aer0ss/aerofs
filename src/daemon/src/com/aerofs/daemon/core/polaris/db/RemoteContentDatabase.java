/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.daemon.core.store.IStoreCreationOperator;
import com.aerofs.daemon.core.store.IStoreDeletionOperator;
import com.aerofs.daemon.core.store.StoreCreationOperators;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.ParameterizedStatement;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.base.Joiner;
import com.google.inject.Inject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 * Keep track of known versions of content that have not yet been downloaded
 *
 * We allow multiple versions of the same object to exist to allow partial
 * progress to be made while the latest successful writer is offline.
 *
 * This table keeps track not just of know versions but also of the originator
 * of the change and the new properties of the file.
 *
 * Invariants:
 *   - all entries in this table have a version superior or equal to that of the local object
 *   - there is at most one remote version per originating device
 *
 * The entry corresponding to the last downloaded version is kept in the db to make
 * it easier to re-admit an object after an expulsion.
 */
public class RemoteContentDatabase extends AbstractDatabase
        implements IStoreCreationOperator, IStoreDeletionOperator
{
    @Inject
    public RemoteContentDatabase(CoreDBCW dbcw, StoreCreationOperators sco,
            StoreDeletionOperators sdo)
    {
        super(dbcw.get());
        sco.add_(this);
        sdo.addImmediate_(this);
    }

    private static String tableName(SIndex sidx)
    {
        return T_REMOTE_CONTENT + "_" + sidx.getInt();
    }

    @Override
    public void createStore_(SIndex sidx, boolean usePolaris, Trans t) throws SQLException
    {
        if (!usePolaris) return;
        try (Statement s = c().createStatement()) {
            s.executeUpdate("create table " + tableName(sidx) + "("
                    + C_REMOTE_CONTENT_OID + _dbcw.uniqueIdType() + "not null,"
                    + C_REMOTE_CONTENT_VERSION + _dbcw.longType() + "not null,"
                    + C_REMOTE_CONTENT_DID + _dbcw.uniqueIdType() + " not null,"
                    + C_REMOTE_CONTENT_HASH + " blob not null,"
                    + C_REMOTE_CONTENT_LENGTH + _dbcw.longType() + "not null,"
                    + "unique(" + C_REMOTE_CONTENT_OID + "," + C_REMOTE_CONTENT_DID + "),"
                    + "unique(" + C_REMOTE_CONTENT_OID + "," + C_REMOTE_CONTENT_VERSION + ")"
                    + ")");
        }
    }

    @Override
    public void deleteStore_(SIndex sidx, Trans t)
            throws SQLException
    {
        // NB: this is called even if the store is not configured to use Polaris
        // hence the need for "if exists"
        try (Statement s = c().createStatement()) {
            s.executeUpdate("drop table if exists " + tableName(sidx));
        }
    }

    private final ParameterizedStatement<SIndex> _pswInsert = new ParameterizedStatement<>(
            sidx -> "replace into " + tableName(sidx) + "("
                    + Joiner.on(',').join(C_REMOTE_CONTENT_OID,
                            C_REMOTE_CONTENT_VERSION, C_REMOTE_CONTENT_DID, C_REMOTE_CONTENT_HASH,
                            C_REMOTE_CONTENT_LENGTH)
                    + ") values(?,?,?,?,?)");
    public void insert_(SIndex sidx, OID oid, long version, DID did, ContentHash hash,
            long length, Trans t) throws SQLException
    {
        exec(_pswInsert.get(sidx), ps -> {
            ps.setBytes(1, oid.getBytes());
            ps.setLong(2, version);
            ps.setBytes(3, did.getBytes());
            ps.setBytes(4, hash.getBytes());
            ps.setLong(5, length);
            checkState(ps.executeUpdate() == 1);
            return null;
        });
    }



    private final ParameterizedStatement<SIndex> _pswDelete = new ParameterizedStatement<>(
            sidx -> DBUtil.deleteWhere(tableName(sidx),
                    C_REMOTE_CONTENT_OID + "=? and " + C_REMOTE_CONTENT_VERSION + "<?"));
    public boolean deleteUpToVersion_(SIndex sidx, OID oid, long version, Trans t) throws SQLException
    {
        return exec(_pswDelete.get(sidx), ps -> {
            ps.setBytes(1, oid.getBytes());
            ps.setLong(2, version);
            return ps.executeUpdate() > 0;
        });
    }

    private final ParameterizedStatement<SIndex> _pswHasNew = new ParameterizedStatement<>(
            sidx -> DBUtil.selectWhere(tableName(sidx),
                    C_REMOTE_CONTENT_OID + "=? and " + C_REMOTE_CONTENT_VERSION + ">?", "1")
                    + " limit 1");
    public boolean hasRemoteChanges_(SIndex sidx, OID oid, long version) throws SQLException
    {
        return exec(_pswHasNew.get(sidx), ps -> {
            ps.setBytes(1, oid.getBytes());
            ps.setLong(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        });
    }

    private final ParameterizedStatement<SIndex> _pswHas = new ParameterizedStatement<>(
            sidx -> DBUtil.selectWhere(tableName(sidx),
                    C_REMOTE_CONTENT_OID + "=? and " + C_REMOTE_CONTENT_VERSION + "=?", "1"));
    public boolean hasRemoteChange_(SIndex sidx, OID oid, long version) throws SQLException
    {
        return exec(_pswHas.get(sidx), ps -> {
            ps.setBytes(1, oid.getBytes());
            ps.setLong(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        });
    }

    private final ParameterizedStatement<SIndex> _pswGetOriginator = new ParameterizedStatement<>(
            sidx -> DBUtil.selectWhere(tableName(sidx), C_REMOTE_CONTENT_OID + "=?",
                    C_REMOTE_CONTENT_DID)
            + " order by " + C_REMOTE_CONTENT_VERSION + " limit 1");
    public DID getOriginator_(SOID soid) throws SQLException
    {
        return exec(_pswGetOriginator.get(soid.sidx()), ps -> {
            ps.setBytes(1, soid.oid().getBytes());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new DID(rs.getBytes(1)) : null;
            }
        });
    }

    private final ParameterizedStatement<SIndex> _pswGetMaxVersion = new ParameterizedStatement<>(
            sidx -> DBUtil.selectWhere(tableName(sidx), C_REMOTE_CONTENT_OID + "=?",
                    "max(" + C_REMOTE_CONTENT_VERSION + ")"));
    public Long getMaxVersion_(SIndex sidx, OID oid) throws SQLException
    {
        return exec(_pswGetMaxVersion.get(sidx), ps -> {
            ps.setBytes(1, oid.getBytes());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        });
    }
}
