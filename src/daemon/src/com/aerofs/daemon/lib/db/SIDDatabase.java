package com.aerofs.daemon.lib.db;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import javax.annotation.Nonnull;

/**
 * TODO split this giant class into smaller ones with better defined responsibilities, similar to
 * what we have done for, say, IVersionDatabase, IAliasDatabase, etc.
 */
public class SIDDatabase extends AbstractDatabase implements ISIDDatabase
{
    @Inject
    public SIDDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private PreparedStatement _psGetSIndex;
    @Override
    public SIndex getSIndex_(SID sid) throws SQLException
    {
        try {
            if (_psGetSIndex == null) {
                _psGetSIndex = c().prepareStatement("select " + C_SID_SIDX + " from " + T_SID +
                        " where " + C_SID_SID + "=?");
            }

            _psGetSIndex.setBytes(1, sid.getBytes());
            ResultSet rs = _psGetSIndex.executeQuery();
            try {
                if (rs.next()) {
                    SIndex sidx = new SIndex(rs.getInt(1));
                    assert !rs.next();
                    return sidx;
                } else {
                    return null;
                }
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            DBUtil.close(_psGetSIndex);
            _psGetSIndex = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGetSID;
    @Override
    public @Nonnull SID getSID_(SIndex sidx) throws SQLException
    {
        try {
            if (_psGetSID == null) {
                _psGetSID = c().prepareStatement("select " + C_SID_SID + " from " + T_SID +
                        " where " + C_SID_SIDX + "=?");
            }

            _psGetSID.setInt(1, sidx.getInt());
            ResultSet rs = _psGetSID.executeQuery();
            try {
                Util.verify(rs.next());
                SID sid = new SID(rs.getBytes(1));
                assert !rs.next();
                return sid;
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            DBUtil.close(_psGetSID);
            _psGetSID = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psAdd;
    @Override
    public @Nonnull SIndex insertSID_(SID sid, Trans t) throws SQLException
    {
        try {
            if (_psAdd == null) {
                _psAdd = c().prepareStatement("insert into " + T_SID + "(" + C_SID_SID + ")" +
                        " values(?)", PreparedStatement.RETURN_GENERATED_KEYS);
            }

            _psAdd.setBytes(1, sid.getBytes());
            Util.verify(_psAdd.executeUpdate() == 1);

            ResultSet rs = _psAdd.getGeneratedKeys();
            try {
                Util.verify(rs.next());
                return new SIndex(rs.getInt(1));
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            DBUtil.close(_psAdd);
            _psAdd = null;
            throw detectCorruption(e);
        }
    }

}
