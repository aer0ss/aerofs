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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.core.polaris.db.PolarisSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 * Keep track of objects with local changes
 */
public class ContentChangesDatabase extends AbstractDatabase
        implements IStoreCreationOperator, IStoreDeletionOperator
{
    @Inject
    public ContentChangesDatabase(IDBCW dbcw, StoreCreationOperators sco,
            StoreDeletionOperators sdo)
    {
        super(dbcw);
        sco.add_(this);
        sdo.addImmediate_(this);
    }

    private static String tableName(SIndex sidx)
    {
        return T_CONTENT_CHANGE + "_" + sidx.getInt();
    }

    @Override
    public void createStore_(SIndex sidx, Trans t) throws SQLException
    {
        try (Statement s = c().createStatement()) {
            s.executeUpdate("create table " + tableName(sidx) + "("
                    + C_CONTENT_CHANGE_IDX + _dbcw.longType() + " primary key " + _dbcw.autoIncrement() + ","
                    + C_CONTENT_CHANGE_OID + _dbcw.longType() + " unique not null"
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
            sidx -> "replace into " + tableName(sidx) + "(" + C_CONTENT_CHANGE_OID + ") values(?)");
    public long insertChange_(SIndex sidx, OID oid, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswInsert.get(sidx).get(c());
            ps.setBytes(1, oid.getBytes());
            checkState(ps.executeUpdate() == 1);
            try (ResultSet rs = ps.getGeneratedKeys()) {
                checkState(rs.next());
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            _pswInsert.get(sidx).close();
            throw detectCorruption(e);
        }
    }

    private final ParameterizedStatement<SIndex> _pswDeleteObject = new ParameterizedStatement<>(
            sidx -> DBUtil.deleteWhereEquals(tableName(sidx), C_CONTENT_CHANGE_OID));
    public boolean deleteChange_(SIndex sidx, OID oid, Trans t) throws SQLException
    {
        return 1 == update(_pswDeleteObject.get(sidx), oid.getBytes());
    }

    private final ParameterizedStatement<SIndex> _pswHasChange = new ParameterizedStatement<>(
            sidx -> DBUtil.selectWhere(tableName(sidx), C_CONTENT_CHANGE_OID + "=?", "count(*)"));
    public boolean hasChange_(SIndex sidx, OID oid) throws SQLException
    {
        try (ResultSet rs = query(_pswHasChange.get(sidx), oid.getBytes())) {
            return DBUtil.binaryCount(rs);
        }
    }

    public static class ContentChange
    {
        public final SIndex sidx;
        public final long idx;
        public final OID oid;

        ContentChange(SIndex sidx, long idx, OID oid)
        {
            this.sidx = sidx;
            this.idx = idx;
            this.oid = oid;
        }

        @Override
        public String toString() {
            return "{" + Joiner.on(",").join(idx, oid) + "}";
        }
    }

    private final ParameterizedStatement<SIndex> _pswList = new ParameterizedStatement<>(
            sidx -> DBUtil.select(tableName(sidx),
                    C_CONTENT_CHANGE_IDX, C_CONTENT_CHANGE_OID)
            + " order by " + C_CONTENT_CHANGE_IDX);
    public IDBIterator<ContentChange> getChanges_(SIndex sidx) throws SQLException
    {
        return new AbstractDBIterator<ContentChange>(query(_pswList.get(sidx))) {
            @Override
            public ContentChange get_() throws SQLException
            {
                return new ContentChange(sidx, _rs.getLong(1), new OID(_rs.getBytes(2)));
            }
        };
    }

    private final ParameterizedStatement<SIndex> _pswDelete = new ParameterizedStatement<>(
            sidx -> DBUtil.deleteWhere(tableName(sidx), C_CONTENT_CHANGE_IDX + "=?"));
    public boolean deleteChange_(SIndex sidx, long idx, Trans t) throws SQLException
    {
        return 1 == update(_pswDelete.get(sidx), idx);
    }
}
