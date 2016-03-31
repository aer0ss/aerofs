/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.daemon.core.store.IStoreDeletionOperator;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import java.sql.SQLException;

import static com.aerofs.daemon.core.polaris.db.PolarisSchema.C_AVAILABLE_CONTENT_OID;
import static com.aerofs.daemon.core.polaris.db.PolarisSchema.C_AVAILABLE_CONTENT_SIDX;
import static com.aerofs.daemon.core.polaris.db.PolarisSchema.C_AVAILABLE_CONTENT_VERSION;
import static com.aerofs.daemon.core.polaris.db.PolarisSchema.T_AVAILABLE_CONTENT;

/**
 * Keep track of content that has been updated with the latest version
 */
public class AvailableContentDatabase extends AbstractDatabase implements IStoreDeletionOperator
{
    @Inject
    public AvailableContentDatabase(IDBCW dbcw, StoreDeletionOperators sdo)
    {
        super(dbcw);
        sdo.addImmediate_(this);
    }

    private final PreparedStatementWrapper _pswListContent = new PreparedStatementWrapper(
            DBUtil.select(T_AVAILABLE_CONTENT, C_AVAILABLE_CONTENT_SIDX, C_AVAILABLE_CONTENT_OID, C_AVAILABLE_CONTENT_VERSION));
    public IDBIterator<AvailableContent> listContent_() throws SQLException
    {
        return new AbstractDBIterator<AvailableContent>(query(_pswListContent)) {
            @Override
            public AvailableContent get_() throws SQLException
            {
                return new AvailableContent(new SIndex(_rs.getInt(1)), new OID(_rs.getBytes(2)), _rs.getLong(3));
            }
        };
    }

    private final PreparedStatementWrapper _pswSetContent = new PreparedStatementWrapper(
            DBUtil.insertOrReplaceInto(T_AVAILABLE_CONTENT, C_AVAILABLE_CONTENT_SIDX, C_AVAILABLE_CONTENT_OID, C_AVAILABLE_CONTENT_VERSION));
    public boolean setContent_(SIndex sidx, OID oid, long version, Trans t) throws SQLException
    {
        return 1 == update(_pswSetContent, sidx.getInt(), oid.getBytes(), version);
    }

    private final PreparedStatementWrapper _pswDeleteContent = new PreparedStatementWrapper(
            DBUtil.deleteWhereEquals(T_AVAILABLE_CONTENT, C_AVAILABLE_CONTENT_SIDX,
                    C_AVAILABLE_CONTENT_OID, C_AVAILABLE_CONTENT_VERSION));
    public boolean deleteContent_(SIndex sidx, OID oid, long version, Trans t) throws SQLException
    {
        return 1 == update(_pswDeleteContent, sidx.getInt(), oid.getBytes(), version);
    }

    private final PreparedStatementWrapper _pswDeleteStoreContent = new PreparedStatementWrapper(
            DBUtil.deleteWhereEquals(T_AVAILABLE_CONTENT, C_AVAILABLE_CONTENT_SIDX));
    @Override
    public void deleteStore_(SIndex sidx, Trans t) throws SQLException
    {
        update(_pswDeleteStoreContent, sidx.getInt());
    }

    public static class AvailableContent
    {
        public final SIndex sidx;
        public final OID oid;
        public final long version;

        public AvailableContent(SIndex sidx, OID oid, long version) {
            this.sidx = sidx;
            this.oid = oid;
            this.version = version;
        }
    }
}
