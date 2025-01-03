package com.aerofs.daemon.lib.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.ids.DID;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import javax.annotation.Nullable;

public class CollectorFilterDatabase extends AbstractDatabase implements ICollectorFilterDatabase
{
    @Inject
    public CollectorFilterDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    private PreparedStatement _psSCF;
    @Override
    public void setCollectorFilter_(SIndex sidx, DID did, BFOID filter, Trans t)
        throws SQLException
    {
        try {
            if (_psSCF == null) _psSCF = c()
                    .prepareStatement("replace into " + SyncSchema.T_CF + "("
                            + SyncSchema.C_CF_SIDX + "," + SyncSchema.C_CF_DID + "," + SyncSchema.C_CF_FILTER +
                            ") values (?,?,?)");

            _psSCF.setInt(1, sidx.getInt());
            _psSCF.setBytes(2, did.getBytes());
            _psSCF.setBytes(3, filter.getBytes());
            _psSCF.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psSCF);
            _psSCF = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGCF;
    @Override
    public @Nullable BFOID getCollectorFilter_(SIndex sidx, DID did) throws SQLException
    {
        try {
            if (_psGCF == null) _psGCF = c()
                    .prepareStatement("select " + SyncSchema.C_CF_FILTER + " from "
                            + SyncSchema.T_CF + " where " + SyncSchema.C_CF_SIDX + "=? and "
                            + SyncSchema.C_CF_DID + "=?");
            _psGCF.setInt(1, sidx.getInt());
            _psGCF.setBytes(2, did.getBytes());
            ResultSet rs = _psGCF.executeQuery();
            try {
                if (rs.next()) {
                    return new BFOID(rs.getBytes(1));
                } else {
                    return null;
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psGCF);
            _psGCF = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psDCF;
    @Override
    public void deleteCollectorFilter_(SIndex sidx, DID did, Trans t)
        throws SQLException
    {
        try {
            if (_psDCF == null) _psDCF = c()
                    .prepareStatement("delete from " + SyncSchema.T_CF +
                            " where " + SyncSchema.C_CF_SIDX + "=? and "
                            + SyncSchema.C_CF_DID + "=?");

            _psDCF.setInt(1, sidx.getInt());
            _psDCF.setBytes(2, did.getBytes());
            _psDCF.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psDCF);
            _psDCF = null;
            throw detectCorruption(e);
        }
    }

    @Override
    public void deleteCollectorFiltersForStore_(SIndex sidx, Trans t)
            throws SQLException
    {
        StoreDatabase.deleteRowsInTableForStore_(SyncSchema.T_CF, SyncSchema.C_CF_SIDX, sidx, c(), t);
    }
}
