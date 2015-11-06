package com.aerofs.daemon.lib.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.aerofs.daemon.core.collector.SenderFilterIndex;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.ids.DID;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SenderFilterDatabase extends AbstractDatabase implements ISenderFilterDatabase
{
    @Inject
    public SenderFilterDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    private PreparedStatement _psSSF;
    @Override
    public void setSenderFilter_(SIndex sidx, SenderFilterIndex sfidx, BFOID filter, Trans t)
            throws SQLException
    {
        try {
            if (_psSSF == null) _psSSF = c()
                    .prepareStatement("replace into " + SyncSchema.T_SF + "(" +
                            SyncSchema.C_SF_SIDX + "," + SyncSchema.C_SF_SFIDX + "," + SyncSchema.C_SF_FILTER +
                            ") values (?,?,?)");
            _psSSF.setInt(1, sidx.getInt());
            _psSSF.setLong(2, sfidx.getLong());
            _psSSF.setBytes(3, filter.getBytes());
            _psSSF.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psSSF);
            _psSSF = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psDSF;
    @Override
    public void deleteSenderFilter_(SIndex sidx, SenderFilterIndex sfidx,
            Trans t) throws SQLException
    {
        try {
            if (_psDSF == null) _psDSF = c()
                    .prepareStatement("delete from " + SyncSchema.T_SF + " where " +
                            SyncSchema.C_SF_SIDX + "=? and " + SyncSchema.C_SF_SFIDX + "=?");
            _psDSF.setInt(1, sidx.getInt());
            _psDSF.setLong(2, sfidx.getLong());
            _psDSF.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psDSF);
            _psDSF = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGSF;
    @Override
    public @Nullable BFOID getSenderFilter_(SIndex sidx, SenderFilterIndex sfidx)
            throws SQLException
    {
        try {
            if (_psGSF == null) _psGSF = c()
                    .prepareStatement("select " + SyncSchema.C_SF_FILTER + " from "
                            + SyncSchema.T_SF + " where " + SyncSchema.C_SF_SIDX + "=? and " +
                            SyncSchema.C_SF_SFIDX + "=?");
            _psGSF.setInt(1, sidx.getInt());
            _psGSF.setLong(2, sfidx.getLong());
            try (ResultSet rs = _psGSF.executeQuery()) {
                if (rs.next()) {
                    return new BFOID(rs.getBytes(1));
                } else if (sfidx.equals(SenderFilterIndex.BASE)) {
                    return new BFOID();
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            DBUtil.close(_psGSF);
            _psGSF = null;
            throw detectCorruption(e);
        }
    }

    private static class DBIterSenderFilter extends AbstractDBIterator<BFOID>
    {
        DBIterSenderFilter(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public BFOID get_() throws SQLException
        {
            return new BFOID(_rs.getBytes(1));
        }
    }

    private PreparedStatement _psGSFS;
    @Override
    public DBIterSenderFilter getSenderFilters_(SIndex sidx,
            SenderFilterIndex from, SenderFilterIndex to)
            throws SQLException
    {
        try {
            if (_psGSFS == null) _psGSFS = c()
                    .prepareStatement("select " + SyncSchema.C_SF_FILTER + " from "
                            + SyncSchema.T_SF + " where " + SyncSchema.C_SF_SIDX + "=? and " +
                            SyncSchema.C_SF_SFIDX + ">=? and " + SyncSchema.C_SF_SFIDX + "<?");
            _psGSFS.setInt(1, sidx.getInt());
            _psGSFS.setLong(2, from.getLong());
            _psGSFS.setLong(3, to.getLong());
            return new DBIterSenderFilter(_psGSFS.executeQuery());
        } catch (SQLException e) {
            DBUtil.close(_psGSFS);
            _psGSFS = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGSFPI;
    @Override
    public SenderFilterIndex getSenderFilterPreviousIndex_(SIndex sidx,
            SenderFilterIndex sfidx) throws SQLException
    {
        try {
            if (_psGSFPI == null) _psGSFPI = c()
                    .prepareStatement("select max(" + SyncSchema.C_SF_SFIDX + ") from "
                            + SyncSchema.T_SF + " where " + SyncSchema.C_SF_SIDX + "=? and "
                            + SyncSchema.C_SF_SFIDX + "<?");
            _psGSFPI.setInt(1, sidx.getInt());
            _psGSFPI.setLong(2, sfidx.getLong());
            try (ResultSet rs = _psGSFPI.executeQuery()) {
                Util.verify(rs.next());
                return new SenderFilterIndex(rs.getLong(1));
            }
        } catch (SQLException e) {
            DBUtil.close(_psGSFPI);
            _psGSFPI = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGSFGI;
    @Override
    public @Nonnull SenderFilterIndex getSenderFilterGreatestIndex_(SIndex sidx)
        throws SQLException
    {
        try {
            if (_psGSFGI == null) _psGSFGI = c()
                    .prepareStatement("select max(" + SyncSchema.C_SF_SFIDX + ") from "
                            + SyncSchema.T_SF + " where " + SyncSchema.C_SF_SIDX + "=?");
            _psGSFGI.setInt(1, sidx.getInt());
            try (ResultSet rs = _psGSFGI.executeQuery()) {
                if (rs.next()) {
                    long v = rs.getLong(1);
                    return rs.wasNull() ? SenderFilterIndex.BASE : new SenderFilterIndex(v);
                } else {
                    return SenderFilterIndex.BASE;
                }
            }
        } catch (SQLException e) {
            DBUtil.close(_psGSFGI);
            _psGSFGI = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGSDI;
    @Override
    public SenderFilterIndex getSenderDeviceIndex_(SIndex sidx, DID did)
            throws SQLException
    {
        try {
            if (_psGSDI == null) _psGSDI = c()
                    .prepareStatement("select " + SyncSchema.C_SD_SFIDX + " from "
                            + SyncSchema.T_SD + " where " + SyncSchema.C_SD_SIDX + "=? and " +
                            SyncSchema.C_SD_DID + "=?");
            _psGSDI.setInt(1, sidx.getInt());
            _psGSDI.setBytes(2, did.getBytes());
            try (ResultSet rs = _psGSDI.executeQuery()) {
                if (rs.next()) {
                    long v = rs.getLong(1);
                    return rs.wasNull() ? null : new SenderFilterIndex(v);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            DBUtil.close(_psGSDI);
            _psGSDI = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGSDIC;
    @Override
    public int getSenderDeviceIndexCount_(SIndex sidx, SenderFilterIndex sfidx)
            throws SQLException
    {
        try {
            if (_psGSDIC == null) _psGSDIC = c()
                    .prepareStatement("select count(*) from "
                            + SyncSchema.T_SD + " where " + SyncSchema.C_SD_SIDX + "=? and " +
                            SyncSchema.C_SD_SFIDX + "=?");
            _psGSDIC.setInt(1, sidx.getInt());
            _psGSDIC.setLong(2, sfidx.getLong());
            try (ResultSet rs = _psGSDIC.executeQuery()) {
                Util.verify(rs.next());
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            DBUtil.close(_psGSDIC);
            _psGSDIC = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psSSDI;
    @Override
    public void setSenderDeviceIndex_(SIndex sidx, DID did,
            SenderFilterIndex sfidx, Trans t)
        throws SQLException
    {
        try {
            if (_psSSDI == null) _psSSDI = c()
                    .prepareStatement("replace into " + SyncSchema.T_SD + "(" +
                            SyncSchema.C_SD_SIDX + "," + SyncSchema.C_SD_DID + "," + SyncSchema.C_SD_SFIDX +
                            ") values (?,?,?)");
            _psSSDI.setInt(1, sidx.getInt());
            _psSSDI.setBytes(2, did.getBytes());
            _psSSDI.setLong(3, sfidx.getLong());
            _psSSDI.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psSSDI);
            _psSSDI = null;
            throw detectCorruption(e);
        }
    }

    @Override
    public void deleteSenderFiltersAndDevicesForStore_(SIndex sidx, Trans t)
            throws SQLException
    {
        StoreDatabase.deleteRowsInTablesForStore_(
                ImmutableMap.of(SyncSchema.T_SF, SyncSchema.C_SF_SIDX, SyncSchema.T_SD, SyncSchema.C_SD_SIDX), sidx, c(), t);
    }
}
