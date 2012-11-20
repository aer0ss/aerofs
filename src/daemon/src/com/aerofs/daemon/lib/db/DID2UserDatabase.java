/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.id.DID;
import com.google.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.CoreSchema.C_D2U_DID;
import static com.aerofs.daemon.lib.db.CoreSchema.C_D2U_USER;
import static com.aerofs.daemon.lib.db.CoreSchema.T_D2U;

/**
 * When possible, use the DID2User class which provides a high-level wrapper around this
 * low-level class.
 */
public class DID2UserDatabase extends AbstractDatabase implements IDID2UserDatabase
{
    @Inject
    public DID2UserDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private PreparedStatement _psAdd;
    @Override
    public void add_(DID did, String user, Trans t)
        throws SQLException
    {
        try {
            if (_psAdd == null) _psAdd = c().prepareStatement("insert into " + T_D2U + " ( " +
                    C_D2U_DID + "," + C_D2U_USER + ") values (?,?)");

            _psAdd.setBytes(1, did.getBytes());
            _psAdd.setString(2, user);

            _psAdd.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psAdd);
            _psAdd = null;
            throw e;
        }
    }

    private PreparedStatement _psGet;
    @Override
    public String getNullable_(DID did)
        throws SQLException
    {
        try {
            if (_psGet == null) _psGet = c().prepareStatement("select " + C_D2U_USER + " from " +
                    T_D2U + " where " + C_D2U_DID + "=?");
            _psGet.setBytes(1, did.getBytes());
            ResultSet rs = _psGet.executeQuery();
            try {
                if (rs.next()) {
                    String user = rs.getString(1);
                    assert !rs.next();
                    return user;
                } else {
                    return null;
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psGet);
            _psGet = null;
            throw e;
        }
    }
}
