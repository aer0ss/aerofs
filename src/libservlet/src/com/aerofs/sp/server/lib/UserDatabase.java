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
import java.sql.Timestamp;

import static com.aerofs.lib.db.DBUtil.count;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_TI_FROM;
import static com.aerofs.sp.server.lib.SPSchema.C_TI_ORG_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_TI_TIC;
import static com.aerofs.sp.server.lib.SPSchema.C_TI_TO;
import static com.aerofs.sp.server.lib.SPSchema.C_TI_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ACL_EPOCH;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_AUTHORIZATION_LEVEL;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_CREDS;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_FIRST_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_LAST_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ORG_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_SIGNUP_INVITATIONS_QUOTA;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_VERIFIED;
import static com.aerofs.sp.server.lib.SPSchema.T_TI;
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

    public void setOrgID(UserID userId, OrgID orgId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                updateWhere(T_USER, C_USER_ID + "=?", C_USER_ORG_ID));

        ps.setInt(1, orgId.getInt());
        ps.setString(2, userId.toString());
        Util.verify(ps.executeUpdate() == 1);
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
            throw new ExNotFound("user " + userId);
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

    // TODO (WW) move it to a different database class?
    public void addSignupCode(String code, UserID from, UserID to, OrgID orgId)
            throws SQLException
    {
        addSignupCode(code, from, to, orgId, System.currentTimeMillis());
    }

    // For testing only
    // TODO (WW) use DI instead
    public void addSignupCode(String code, UserID from, UserID to, OrgID orgId, long currentTime)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_TI, C_TI_TIC, C_TI_FROM, C_TI_TO, C_TI_ORG_ID, C_TI_TS));

        ps.setString(1, code);
        ps.setString(2, from.toString());
        ps.setString(3, to.toString());
        ps.setInt(4, orgId.getInt());
        ps.setTimestamp(5, new Timestamp(currentTime), UTC_CALANDER);
        ps.executeUpdate();
    }

    // Return 0 if user not found.
    public int getSignUpInvitationsQuota(UserID userId)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryUser(userId, C_USER_SIGNUP_INVITATIONS_QUOTA);
        try {
            return rs.getInt(1);
        } finally {
            rs.close();
        }
    }

    public void setSignUpInvitationsQuota(UserID userId, int quota)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                updateWhere(T_USER, C_USER_ID + "=?", C_USER_SIGNUP_INVITATIONS_QUOTA));

        ps.setInt(1, quota);
        ps.setString(2, userId.toString());
        ps.executeUpdate();
    }


    /**
     * Check whether a user has already been invited (with a targeted signup code).
     * This is used by us to avoid spamming people when doing mass-invite
     */
    public boolean isInvitedToSignUp(UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.selectWhere(T_TI, C_TI_TO + "=?", "count(*)"));

        ps.setString(1, userId.toString());
        ResultSet rs = ps.executeQuery();
        try {
            return count(rs) != 0;
        } finally {
            rs.close();
        }
    }
}
