/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.ids.OID;
import com.aerofs.daemon.core.store.IStoreCreationOperator;
import com.aerofs.daemon.core.store.IStoreDeletionOperator;
import com.aerofs.daemon.core.store.StoreCreationOperators;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.ParameterizedStatement;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 *
 */
public class MetaChangesDatabase extends AbstractDatabase
        implements IStoreCreationOperator, IStoreDeletionOperator
{
    @Inject
    public MetaChangesDatabase(CoreDBCW dbcw, StoreCreationOperators sco,
            StoreDeletionOperators sdo)
    {
        super(dbcw.get());
        sco.add_(this);
        sdo.addImmediate_(this);
    }

    private static String tableName(SIndex sidx)
    {
        return T_META_CHANGE + "_" + sidx.getInt();
    }

    @Override
    public void createStore_(SIndex sidx, boolean usePolaris, Trans t) throws SQLException
    {
        if (!usePolaris) return;
        try (Statement s = c().createStatement()) {
            s.executeUpdate("create table " + tableName(sidx) + "("
                    + C_META_CHANGE_IDX + _dbcw.longType() + " primary key " + _dbcw.autoIncrement() + ","
                    + C_META_CHANGE_OID + _dbcw.uniqueIdType() + "not null,"
                    + C_META_CHANGE_NEW_PARENT + _dbcw.uniqueIdType() + ","
                    + C_META_CHANGE_NEW_NAME + _dbcw.nameType()
                    + ")");

            s.executeUpdate("create index " + tableName(sidx) + "_0 on " + tableName(sidx)
                    + "(" + C_META_CHANGE_OID + ")");

        }
    }

    @Override
    public void deleteStore_(SIndex sidx, Trans t) throws SQLException
    {
        // NB: this is called even if the store is not configured to use Polaris
        // hence the need for "if exists"
        try (Statement s = c().createStatement()) {
            s.executeUpdate("drop table if exists " + tableName(sidx));
        }
    }

    private final ParameterizedStatement<SIndex> _pswInsertChange = new ParameterizedStatement<>(
            sidx ->  DBUtil.insert(tableName(sidx), C_META_CHANGE_OID,
                    C_META_CHANGE_NEW_PARENT, C_META_CHANGE_NEW_NAME));
    public long insertChange_(SIndex sidx, OID oid,  @Nullable OID newParent,
            @Nullable String newName, Trans t) throws SQLException
    {
        return exec(_pswInsertChange.get(sidx), ps -> {
            ps.setBytes(1, oid.getBytes());
            ps.setBytes(2, newParent != null ? newParent.getBytes() : null);
            ps.setString(3, newName);
            checkState(ps.executeUpdate() == 1);
            try (ResultSet rs = ps.getGeneratedKeys()) {
                checkState(rs.next());
                return rs.getLong(1);
            }
        });
    }

    public static class MetaChange
    {
        public final SIndex sidx;
        public final long idx;
        public final OID oid;
        public OID newParent;
        public final String newName;

        public MetaChange(SIndex sidx, long idx, OID oid, byte[] newParent, String newName)
        {
            this.sidx = sidx;
            this.idx = idx;
            this.oid = oid;
            this.newParent = newParent != null ? new OID(newParent) : null;
            this.newName = newName;
        }
    }

    private final ParameterizedStatement<SIndex> _pswGetStoreChanges = new ParameterizedStatement<>(
            sidx -> DBUtil.selectWhere(tableName(sidx),
                    C_META_CHANGE_IDX + ">?",
                    C_META_CHANGE_IDX, C_META_CHANGE_OID,
                    C_META_CHANGE_NEW_PARENT, C_META_CHANGE_NEW_NAME)
            + " order by " + C_META_CHANGE_IDX);
    public IDBIterator<MetaChange> getChangesSince_(SIndex sidx, long idx) throws SQLException
    {
        return exec(_pswGetStoreChanges.get(sidx), ps -> {
            ps.setLong(1, idx);
            return new AbstractDBIterator<MetaChange>(ps.executeQuery()) {
                @Override
                public MetaChange get_() throws SQLException
                {
                    return new MetaChange(sidx, _rs.getLong(1), new OID(_rs.getBytes(2)),
                            _rs.getBytes(3), _rs.getString(4));
                }
            };
        });
    }

    public boolean hasChanges_(SIndex sidx) throws SQLException
    {
        try (IDBIterator<MetaChange> it = getChangesSince_(sidx, 0)) {
            return it.next_();
        }
    }

    private final ParameterizedStatement<SIndex> _pswDeleteChange = new ParameterizedStatement<>(
            sidx -> DBUtil.deleteWhere(tableName(sidx), C_META_CHANGE_IDX + "=?"));
    public boolean deleteChange_(SIndex sidx, long idx, Trans t) throws SQLException
    {
        return exec(_pswDeleteChange.get(sidx), ps -> {
            ps.setLong(1, idx);
            return ps.executeUpdate() == 1;
        });
    }

    private final ParameterizedStatement<SIndex> _pswDeleteObjectChanges = new ParameterizedStatement<>(
            sidx -> DBUtil.deleteWhere(tableName(sidx), C_META_CHANGE_OID + "=?"));
    public boolean deleteChanges_(SIndex sidx, OID oid, Trans t) throws SQLException
    {
        return exec(_pswDeleteObjectChanges.get(sidx), ps -> {
            ps.setBytes(1, oid.getBytes());
            return ps.executeUpdate() == 1;
        });
    }
}
