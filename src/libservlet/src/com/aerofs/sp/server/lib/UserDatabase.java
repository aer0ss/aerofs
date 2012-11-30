/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.Base64;
import com.aerofs.lib.C;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.UserID;
import com.aerofs.servlets.lib.db.AbstractSQLDatabase;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ACL_EPOCH;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_AUTHORIZATION_LEVEL;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_CREDS;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_FIRST_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_LAST_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ORG_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_VERIFIED;
import static com.aerofs.sp.server.lib.SPSchema.T_USER;

/**
 * N.B. only User.java may refer to this class
 */
public class UserDatabase extends AbstractSQLDatabase
{
    public UserDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    /**
     * Note: this method always creates the user as non-verified.
     *
     * @throws ExAlreadyExist if the user ID already exists
     */
    public void addUser(UserID id, FullName fullName, byte[] shaedSP, OrgID orgID,
            AuthorizationLevel level)
            throws SQLException, ExAlreadyExist
    {
        try {
            // we always create a user with initial epoch + 1 to ensure that the first time
            // a device is created it gets any acl updates that were made while the user
            // didn't have an entry in the user table

            PreparedStatement psAU = prepareStatement(
                    DBUtil.insert(T_USER, C_USER_ID, C_USER_CREDS, C_USER_FIRST_NAME,
                            C_USER_LAST_NAME, C_USER_ORG_ID, C_USER_AUTHORIZATION_LEVEL,
                            C_USER_ACL_EPOCH));

            psAU.setString(1, id.toString());
            psAU.setString(2, Base64.encodeBytes(shaedSP));
            psAU.setString(3, fullName._first);
            psAU.setString(4, fullName._last);
            psAU.setInt(5, orgID.getInt());
            psAU.setInt(6, level.ordinal());
            //noinspection PointlessArithmeticExpression
            psAU.setInt(7, C.INITIAL_ACL_EPOCH + 1);
            psAU.executeUpdate();
        } catch (SQLException aue) {
            throwOnConstraintViolation(aue);
        }
    }

    public boolean hasUser(UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_USER, C_USER_ID + "=?", "count(*)"));
        ps.setString(1, userId.toString());
        ResultSet rs = ps.executeQuery();
        try {
            Util.verify(rs.next());
            int count = rs.getInt(1);
            assert count == 0 || count == 1;
            assert !rs.next();
            return count != 0;
        } finally {
            rs.close();
        }
    }

    public @Nonnull OrgID getOrgID(UserID userId)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryUser(userId, C_USER_ORG_ID);
        try {
            return new OrgID(rs.getInt(1));
        } finally {
            rs.close();
        }
    }

    public @Nonnull FullName getFullName(UserID userId)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryUser(userId, C_USER_FIRST_NAME, C_USER_LAST_NAME);
        try {
            return new FullName(rs.getString(1), rs.getString(2));
        } finally {
            rs.close();
        }
    }

    public @Nonnull byte[] getShaedSP(UserID userId)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryUser(userId, C_USER_CREDS);
        try {
            return Base64.decode(rs.getString(1));
        } catch (IOException e) {
            // Base64.decode should not throw any way.
            throw new SQLException(e);
        } finally {
            rs.close();
        }
    }

    public boolean isVerified(UserID userId)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryUser(userId, C_USER_VERIFIED);
        try {
            return rs.getBoolean(1);
        } finally {
            rs.close();
        }
    }

    public @Nonnull AuthorizationLevel getLevel(UserID userId)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryUser(userId, C_USER_AUTHORIZATION_LEVEL);
        try {
            return AuthorizationLevel.fromOrdinal(rs.getInt(1));
        } finally {
            rs.close();
        }
    }

    private ResultSet queryUser(UserID userId, String ... fields)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_USER, C_USER_ID + "=?", fields));
        ps.setString(1, userId.toString());
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            throw new ExNotFound("user #" + userId);
        } else {
            return rs;
        }
    }

    public void setVerified(UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement("update " +
                T_USER + " set " + C_USER_VERIFIED + "=true where " + C_USER_ID + "=?");

        ps.setString(1, userId.toString());
        Util.verify(ps.executeUpdate() == 1);
    }

    public void setLevel(UserID userId, AuthorizationLevel authLevel)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                updateWhere(T_USER, C_USER_ID + "=?", C_USER_AUTHORIZATION_LEVEL));

        ps.setInt(1, authLevel.ordinal());
        ps.setString(2, userId.toString());
        Util.verify(ps.executeUpdate() == 1);
    }

    public void setName(UserID userId, FullName fullName)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                updateWhere(T_USER, C_USER_ID + "=?", C_USER_FIRST_NAME, C_USER_LAST_NAME));

        // TODO (WW) instead of doing trim here, normalize the FullName at entry points.
        // See UserID.fromInternal/fromExternal
        ps.setString(1, fullName._first.trim());
        ps.setString(2, fullName._last.trim());
        ps.setString(3, userId.toString());
        Util.verify(ps.executeUpdate() == 1);
    }
}
