/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.FullName;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.ids.UserID;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.annotation.Nullable;

import static com.aerofs.daemon.lib.db.SyncSchema.*;

/**
 * When possible, use the UserAndDeviceNames class which provides a high-level wrapper around this
 * low-level class.
 */
public class UserAndDeviceNameDatabase
        extends AbstractDatabase
        implements IUserAndDeviceNameDatabase
{
    @Inject
    public UserAndDeviceNameDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    private PreparedStatement _psSDN;
    @Override
    public void setDeviceName_(DID did, String name, Trans t)
        throws SQLException
    {
        try {
            if (_psSDN == null) _psSDN = c().prepareStatement("replace into " + T_DN + " ( " +
                    C_DN_DID + "," + C_DN_NAME + "," + C_DN_TIME + ") values (?,?,?)");

            _psSDN.setBytes(1, did.getBytes());
            _psSDN.setString(2, name);
            _psSDN.setLong(3, System.currentTimeMillis());

            _psSDN.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psSDN);
            _psSDN = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psSUN;
    @Override
    public void setUserName_(UserID user, FullName fullName, Trans t)
            throws SQLException
    {
        try {
            if (_psSUN == null) _psSUN = c().prepareStatement("replace into " + T_UN + " ( " +
                    C_UN_USER + "," + C_UN_FIRST_NAME + "," + C_UN_LAST_NAME + "," + C_UN_TIME +
                    ") values (?,?,?,?)");

            _psSUN.setString(1, user.getString());
            _psSUN.setString(2, fullName._first);
            _psSUN.setString(3, fullName._last);
            _psSUN.setLong(4, System.currentTimeMillis());

            _psSUN.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psSUN);
            _psSUN = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGDN;
    @Override
    public @Nullable String getDeviceNameNullable_(DID did)
        throws SQLException
    {
        try {
            if (_psGDN == null) _psGDN = c().prepareStatement("select " + C_DN_NAME +
                    " from " + T_DN + " where " + C_DN_DID + "=?");
            _psGDN.setBytes(1, did.getBytes());
            try (ResultSet rs = _psGDN.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString(1);
                    if (rs.wasNull()) name = null;
                    assert !rs.next();
                    return name;
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            DBUtil.close(_psGDN);
            _psGDN = null;
            throw detectCorruption(e);
        }
    }


    private PreparedStatement _psGUN;
    @Override
    public @Nullable FullName getUserNameNullable_(UserID user)
            throws SQLException
    {
        try {
            if (_psGUN == null) _psGUN = c().prepareStatement("select " + C_UN_FIRST_NAME +
                    "," + C_UN_LAST_NAME + " from " + T_UN + " where " + C_UN_USER + "=?");
            _psGUN.setString(1, user.getString());
            try (ResultSet rs = _psGUN.executeQuery()) {
                if (rs.next()) {
                    FullName fullName = new FullName(rs.getString(1), rs.getString(2));
                    assert !rs.next();
                    return fullName;
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            DBUtil.close(_psGUN);
            _psGUN = null;
            throw detectCorruption(e);
        }
    }


    private PreparedStatement _psCUNC;
    @Override
    public void clearUserNameCache_()
            throws SQLException
    {
        try {
            if (_psCUNC == null) {
                _psCUNC = c().prepareStatement("delete from " + T_UN);
            }
            _psCUNC.execute();
        } catch (SQLException e) {
            DBUtil.close(_psCUNC);
            _psCUNC = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psCDNC;
    @Override
    public void clearDeviceNameCache_()
            throws SQLException
    {
        try {
            if (_psCDNC == null) {
                _psCDNC = c().prepareStatement("delete from " + T_DN);
            }
            _psCDNC.execute();
        } catch (SQLException e) {
            DBUtil.close(_psCDNC);
            _psCDNC = null;
            throw detectCorruption(e);
        }
    }
}
