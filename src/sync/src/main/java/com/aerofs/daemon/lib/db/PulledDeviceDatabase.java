package com.aerofs.daemon.lib.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.aerofs.daemon.core.store.IStoreDeletionOperator;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.ids.DID;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

public class PulledDeviceDatabase extends AbstractDatabase implements IPulledDeviceDatabase,
        IStoreDeletionOperator
{
    @Inject
    public PulledDeviceDatabase(IDBCW dbcw, StoreDeletionOperators storeDeletionOperators)
    {
        super(dbcw);
        storeDeletionOperators.addImmediate_(this);
    }

    private PreparedStatement _psPDContains;
    @Override
    public boolean contains_(SIndex sidx, DID did) throws SQLException
    {
        try {
            if (_psPDContains == null) {
                _psPDContains = c().prepareStatement("select count(*) from "
                                  + SyncSchema.T_PD + " where "
                                  + SyncSchema.C_PD_SIDX + "=? and "
                                  + SyncSchema.C_PD_DID + "=?");
            }
            _psPDContains.setInt(1, sidx.getInt());
            _psPDContains.setBytes(2, did.getBytes());
            _psPDContains.executeQuery();

            try (ResultSet rs = _psPDContains.executeQuery()) {
                Util.verify(rs.next());
                int resultRows = rs.getInt(1);
                assert !rs.next();
                return (resultRows == 1);
            }
        } catch (SQLException e) {
            DBUtil.close(_psPDContains);
            _psPDContains = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psAddToPD;
    @Override
    public void insert_(SIndex sidx, DID did, Trans t) throws SQLException
    {
        try {
            if (_psAddToPD == null) {
                _psAddToPD = c().prepareStatement("replace into " + SyncSchema.T_PD + "("
                              + SyncSchema.C_PD_SIDX + "," + SyncSchema.C_PD_DID + ") values (?,?)");
            }

            _psAddToPD.setInt(1, sidx.getInt());
            _psAddToPD.setBytes(2, did.getBytes());
            _psAddToPD.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psAddToPD);
            _psAddToPD = null;
            throw detectCorruption(e);
        }
    }

    /**
     * If a store or a file in a store is re-admitted, we need to "forget" which DIDs have been
     * pulled for filters, so that files can be downloaded again in the Collector algorithm.
     */
    @Override
    public void discardAllDevices_(SIndex sidx, Trans t) throws SQLException
    {
        StoreDatabase.deleteRowsInTableForStore_(SyncSchema.T_PD, SyncSchema.C_PD_SIDX, sidx, c(), t);
    }

    @Override
    public void deleteStore_(SIndex sidx, Trans t)
            throws SQLException
    {
        discardAllDevices_(sidx, t);
    }
}
