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
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.ParameterizedStatement;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.common.base.Joiner;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.core.polaris.db.PolarisSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 *
 */
public class MetaChangesDatabase extends AbstractDatabase
        implements IStoreCreationOperator, IStoreDeletionOperator
{
    @Inject
    public MetaChangesDatabase(IDBCW dbcw, StoreCreationOperators sco,
            StoreDeletionOperators sdo)
    {
        super(dbcw);
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
                    + C_META_CHANGE_MIGRANT + _dbcw.uniqueIdType() + ","
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
                    C_META_CHANGE_NEW_PARENT, C_META_CHANGE_NEW_NAME, C_META_CHANGE_MIGRANT));
    public long insertChange_(SIndex sidx, OID oid,  @Nullable OID newParent,
                              @Nullable String newName, Trans t) throws SQLException
    {
        return insertChange_(sidx, oid, newParent, newName, null, t);
    }

    public long insertChange_(SIndex sidx, OID oid,  @Nullable OID newParent,
            @Nullable String newName, @Nullable OID migrant, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswInsertChange.get(sidx).get(c());
            ps.setBytes(1, oid.getBytes());
            ps.setBytes(2, newParent != null ? newParent.getBytes() : null);
            ps.setString(3, newName);
            ps.setBytes(4, migrant != null ? migrant.getBytes() : null);
            checkState(ps.executeUpdate() == 1);
            try (ResultSet rs = ps.getGeneratedKeys()) {
                checkState(rs.next());
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            _pswInsertChange.get(sidx).close();
            throw detectCorruption(e);
        }
    }

    private final ParameterizedStatement<SIndex> _pswUpdateChanges = new ParameterizedStatement<>(
            sidx ->  DBUtil.updateWhere(tableName(sidx), C_META_CHANGE_OID + "=?", C_META_CHANGE_OID));
    public int updateChanges_(SIndex sidx, OID oid, OID anchor, Trans t) throws SQLException {
        return update(_pswUpdateChanges.get(sidx), anchor.getBytes(), oid.getBytes());
    }

    public static class MetaChange
    {
        public final SIndex sidx;
        public final long idx;
        public final OID oid;
        public final OID migrant;
        public OID newParent;
        public final String newName;

        public MetaChange(SIndex sidx, long idx, OID oid, OID newParent, String newName)
        {
            this(sidx, idx, oid, newParent, newName, null);
        }

        public MetaChange(SIndex sidx, long idx, OID oid, OID newParent, String newName, OID migrant)
        {
            this.sidx = sidx;
            this.idx = idx;
            this.oid = oid;
            this.newParent = newParent;
            this.newName = newName;
            this.migrant = migrant;
        }

        public MetaChange(SIndex sidx, long idx, OID oid, byte[] newParent, String newName, byte[] migrant)
        {
            this(sidx, idx, oid, newParent != null ? new OID(newParent) : null, newName,
                    migrant != null ? new OID(migrant) : null);
        }

        @Override
        public String toString() {
            return "{" + Joiner.on(",").join(sidx, idx, oid, newParent, newName) + "}";
        }
    }

    private final ParameterizedStatement<SIndex> _pswGetStoreChanges = new ParameterizedStatement<>(
            sidx -> DBUtil.selectWhere(tableName(sidx),
                    C_META_CHANGE_IDX + ">?",
                    C_META_CHANGE_IDX, C_META_CHANGE_OID,
                    C_META_CHANGE_NEW_PARENT, C_META_CHANGE_NEW_NAME, C_META_CHANGE_MIGRANT)
            + " order by " + C_META_CHANGE_IDX);
    public IDBIterator<MetaChange> getChangesSince_(SIndex sidx, long idx) throws SQLException
    {
        return new AbstractDBIterator<MetaChange>(query(_pswGetStoreChanges.get(sidx), idx)) {
            @Override
            public MetaChange get_() throws SQLException
            {
                return new MetaChange(sidx, _rs.getLong(1), new OID(_rs.getBytes(2)),
                        _rs.getBytes(3), _rs.getString(4), _rs.getBytes(5));
            }
        };
    }

    public boolean hasChanges_(SIndex sidx) throws SQLException
    {
        try (IDBIterator<MetaChange> it = getChangesSince_(sidx, 0)) {
            return it.next_();
        }
    }

    private final ParameterizedStatement<SIndex> _pswGetObjectChanges = new ParameterizedStatement<>(
            sidx -> DBUtil.selectWhere(tableName(sidx), C_META_CHANGE_OID + "=?", "count(*)"));
    public boolean hasChanges_(SIndex sidx, OID oid) throws SQLException {
        try (ResultSet rs = query(_pswGetObjectChanges.get(sidx), oid.getBytes())) {
            return DBUtil.count(rs) > 0;
        }
    }

    private final ParameterizedStatement<SIndex> _pswDeleteChange = new ParameterizedStatement<>(
            sidx -> DBUtil.deleteWhere(tableName(sidx), C_META_CHANGE_IDX + "=?"));
    public boolean deleteChange_(SIndex sidx, long idx, Trans t) throws SQLException
    {
        return 1 == update(_pswDeleteChange.get(sidx), idx);
    }

    private final ParameterizedStatement<SIndex> _pswDeleteObjectChanges = new ParameterizedStatement<>(
            sidx -> DBUtil.deleteWhere(tableName(sidx), C_META_CHANGE_OID + "=?"));
    public boolean deleteChanges_(SIndex sidx, OID oid, Trans t) throws SQLException {
        return 1 == update(_pswDeleteObjectChanges.get(sidx), oid.getBytes());
    }
}
