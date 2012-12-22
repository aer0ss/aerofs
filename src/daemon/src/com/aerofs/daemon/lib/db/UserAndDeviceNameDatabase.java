/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.FullName;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.base.id.UserID;
import com.google.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.annotation.Nullable;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * When possible, use the MapAlias2Target class which provides a high-level wrapper around this
 * low-level class.
 */
public class UserAndDeviceNameDatabase extends AbstractDatabase
implements IUserAndDeviceNameDatabase
{
    @Inject
    public UserAndDeviceNameDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
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
            throw e;
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

            _psSUN.setString(1, user.toString());
            _psSUN.setString(2, fullName._first);
            _psSUN.setString(3, fullName._last);
            _psSUN.setLong(4, System.currentTimeMillis());

            _psSUN.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psSUN);
            _psSUN = null;
            throw e;
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
            ResultSet rs = _psGDN.executeQuery();
            try {
                if (rs.next()) {
                    String name = rs.getString(1);
                    if (rs.wasNull()) name = null;
                    assert !rs.next();
                    return name;
                } else {
                    return null;
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psGDN);
            _psGDN = null;
            throw e;
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
            _psGUN.setString(1, user.toString());
            ResultSet rs = _psGUN.executeQuery();
            try {
                if (rs.next()) {
                    FullName fullName = new FullName(rs.getString(1), rs.getString(2));
                    assert !rs.next();
                    return fullName;
                } else {
                    return null;
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psGUN);
            _psGUN = null;
            throw e;
        }
    }
}
