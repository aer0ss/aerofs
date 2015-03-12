/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.sp.server.settings.token;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.UserID;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_ST_TOKEN;
import static com.aerofs.sp.server.lib.SPSchema.C_ST_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_ST;

public class UserSettingsTokenDatabase extends AbstractSQLDatabase
{
    @Inject
    public UserSettingsTokenDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public boolean hasToken(UserID uid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_ST, C_ST_USER_ID + "=?", "count(*)"))) {
            ps.setString(1, uid.getString());
            try (ResultSet rs = ps.executeQuery()) {
                return DBUtil.binaryCount(rs);
            }
        }
    }

    public String getToken(UserID uid)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_ST, C_ST_USER_ID + "=?", C_ST_TOKEN))) {
            ps.setString(1, uid.getString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new ExNotFound();
                return rs.getString(1);
            }
        }
    }

    public void insertToken(UserID uid, String token)
            throws SQLException, ExAlreadyExist
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.insert(T_ST, C_ST_USER_ID, C_ST_TOKEN))) {
            ps.setString(1, uid.getString());
            ps.setString(2, token);
            if (ps.executeUpdate() != 1) throw new ExAlreadyExist();
        }
    }

    public void deleteToken(UserID uid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.deleteWhere(T_ST, C_ST_USER_ID + "=?"))) {
            ps.setString(1, uid.getString());
            ps.executeUpdate();
        }
    }
}