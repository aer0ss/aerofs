/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.base.id.OID;
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

import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * Queue of objects for which new content needs to be fetched
 */
public class ContentFetchQueueDatabase extends AbstractDatabase
    implements IStoreCreationOperator, IStoreDeletionOperator
{
    @Inject
    protected ContentFetchQueueDatabase(CoreDBCW dbcw, StoreCreationOperators sco,
            StoreDeletionOperators sdo)
    {
        super(dbcw.get());
        sco.add_(this);
        sdo.addImmediate_(this);
    }

    private String tableName(SIndex sidx)
    {
        return T_CONTENT_QUEUE + "_" + sidx.getInt();
    }

    @Override
    public void createStore_(SIndex sidx, boolean usePolaris, Trans t) throws SQLException
    {
        if (!usePolaris) return;
        try (Statement s = c().createStatement()) {
            s.executeUpdate("create table " + tableName(sidx) + "("
                    + C_CONTENT_QUEUE_IDX + " integer primary key,"
                    + C_CONTENT_QUEUE_OID + _dbcw.uniqueIdType() + " unique"
                    + ")");
        }
    }

    @Override
    public void deleteStore_(SIndex sidx, Trans t) throws SQLException
    {
        try (Statement s = c().createStatement()) {
            s.executeUpdate("drop table if exists " + tableName(sidx));
        }
    }

    public class OIDAndFetchIdx extends OID
    {
        public final long idx;
        public OIDAndFetchIdx(byte[] bs, long idx)
        {
            super(bs);
            this.idx = idx;
        }
    }

    private final ParameterizedStatement<SIndex> _pswInsert = new ParameterizedStatement<>(sidx ->
            "insert or ignore into " + tableName(sidx) + "(" + C_CONTENT_QUEUE_OID + ") values(?)");
    public boolean insert_(SIndex sidx, OID oid, Trans t) throws SQLException
    {
        return exec(_pswInsert.get(sidx), ps -> {
            ps.setBytes(1, oid.getBytes());
            return ps.executeUpdate() == 1;
        });
    }

    private final ParameterizedStatement<SIndex> _pswDelete = new ParameterizedStatement<>(sidx ->
            DBUtil.deleteWhereEquals(tableName(sidx), C_CONTENT_QUEUE_OID));
    public boolean remove_(SIndex sidx, OID oid, Trans t) throws SQLException
    {
        return exec(_pswDelete.get(sidx), ps -> {
            ps.setBytes(1, oid.getBytes());
            return ps.executeUpdate() == 1;
        });
    }

    private final ParameterizedStatement<SIndex> _pswList = new ParameterizedStatement<>(sidx ->
            DBUtil.selectWhere(tableName(sidx), C_CONTENT_QUEUE_IDX + ">?",
                    C_CONTENT_QUEUE_IDX, C_CONTENT_QUEUE_OID)
                    + " order by " + C_CONTENT_QUEUE_IDX);
    public IDBIterator<OIDAndFetchIdx> list_(SIndex sidx, long from) throws SQLException
    {
        return exec(_pswList.get(sidx), ps -> {
            ps.setLong(1, from);
            return new AbstractDBIterator<OIDAndFetchIdx>(ps.executeQuery()) {
                @Override
                public OIDAndFetchIdx get_() throws SQLException
                {
                    return new OIDAndFetchIdx(_rs.getBytes(2), _rs.getLong(1));
                }
            };
        });
    }
}
