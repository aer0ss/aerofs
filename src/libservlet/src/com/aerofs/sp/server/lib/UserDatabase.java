/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.base.Base64;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Param;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.aerofs.sp.server.lib.organization.OrganizationID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

import static com.aerofs.lib.db.DBUtil.count;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_PENDING;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_SHARER;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STORE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_OWNER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_TI_FROM;
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
import static com.aerofs.sp.server.lib.SPSchema.T_AC;
import static com.aerofs.sp.server.lib.SPSchema.T_DEVICE;
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
    public void insertUser(UserID id, FullName fullName, byte[] shaedSP, OrganizationID orgID,
            AuthorizationLevel level)
            throws SQLException, ExAlreadyExist
    {
        // we always create a user with initial epoch + 1 to ensure that the first time
        // a device is created it gets any acl updates that were made while the user
        // didn't have an entry in the user table

        PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_USER, C_USER_ID, C_USER_CREDS, C_USER_FIRST_NAME,
                        C_USER_LAST_NAME, C_USER_ORG_ID, C_USER_AUTHORIZATION_LEVEL,
                        C_USER_ACL_EPOCH));

        ps.setString(1, id.toString());
        ps.setString(2, Base64.encodeBytes(shaedSP));
        ps.setString(3, fullName._first);
        ps.setString(4, fullName._last);
        ps.setInt(5, orgID.getInt());
        ps.setInt(6, level.ordinal());
        //noinspection PointlessArithmeticExpression
        ps.setInt(7, Param.INITIAL_ACL_EPOCH + 1);

        try {
            ps.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "user " + id.toString() + " already exists");
            throw e;
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

    public @Nonnull OrganizationID getOrganizationID(UserID userId)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryUser(userId, C_USER_ORG_ID);
        try {
            return new OrganizationID(rs.getInt(1));
        } finally {
            rs.close();
        }
    }

    public void setOrganizationID(UserID userId, OrganizationID orgId)
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

    /**
     * List all devices belonging to a the provided user.
     */
    public ImmutableList<DID> listDevices(UserID userId)
            throws SQLException, ExFormatError
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_DEVICE, C_DEVICE_OWNER_ID + "=?",
                C_DEVICE_ID));

        ps.setString(1, userId.toString());

        ImmutableList.Builder<DID> builder = ImmutableList.builder();
        ResultSet rs = ps.executeQuery();
        try {
            while (rs.next()) {
                String didString = rs.getString(1);
                builder.add(new DID(didString));
            }
        } finally {
            rs.close();
        }

        return builder.build();
    }

    /**
     * List all devices belonging to a the provided user.
     * @param offset Starting index of the results list from the database.
     * @param maxResults Maximum number of results returned from the database.
     */
    public ImmutableList<DID> listDevices(UserID userId, int offset, int maxResults)
            throws SQLException, ExFormatError
    {
        PreparedStatement ps = prepareStatement("select " + C_DEVICE_ID + " from " + T_DEVICE +
                " where " + C_DEVICE_OWNER_ID + " =? order by " + C_DEVICE_NAME +
                " limit ? offset ?");

        ps.setString(1, userId.toString());
        ps.setInt(2, maxResults);
        ps.setInt(3, offset);

        ImmutableList.Builder<DID> builder = ImmutableList.builder();
        ResultSet rs = ps.executeQuery();
        try {
            while (rs.next()) {
                String didString = rs.getString(1);
                builder.add(new DID(didString));
            }
        } finally {
            rs.close();
        }

        return builder.build();
    }

    /**
     * List all devices belonging to a the provided user.
     * @param offset Starting index of the results list from the database.
     * @param maxResults Maximum number of results returned from the database.
     * @param search Search term that we want to match in the device database.
     */
    public ImmutableList<DID> searchDevices(UserID userId, int offset, int maxResults, String search)
            throws SQLException, ExFormatError
    {
        PreparedStatement ps = prepareStatement("select " + C_DEVICE_ID + " from " + T_DEVICE +
                " where " + C_DEVICE_OWNER_ID + " =? and " + C_DEVICE_NAME + " like ? order by " +
                C_DEVICE_NAME + " limit ? offset ?");

        ps.setString(1, userId.toString());
        ps.setString(2, "%" + search + "%");
        ps.setInt(3, maxResults);
        ps.setInt(4, offset);

        ImmutableList.Builder<DID> builder = ImmutableList.builder();
        ResultSet rs = ps.executeQuery();
        try {
            while (rs.next()) {
                String didString = rs.getString(1);
                builder.add(new DID(didString));
            }
        } finally {
            rs.close();
        }

        return builder.build();
    }

    public int listDevicesCount(UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement("select count(*) from " + T_DEVICE + " where " +
                C_DEVICE_OWNER_ID + "=?");

        ps.setString(1, userId.toString());
        ResultSet rs = ps.executeQuery();
        try {
            return count(rs);
        } finally {
            rs.close();
        }
    }

    public int searchDecvicesCount(UserID userId, String search)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement("select count(*) from " + T_DEVICE + " where " +
                C_DEVICE_OWNER_ID + "=? and " + C_DEVICE_NAME + " like ?");

        ps.setString(1, userId.toString());
        ps.setString(2, "%" + search + "%");

        ResultSet rs = ps.executeQuery();
        try {
            return count(rs);
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
    public void insertSignupCode(String code, UserID from, UserID to)
            throws SQLException
    {
        insertSignupCode(code, from, to, System.currentTimeMillis());
    }

    // For testing only
    // TODO (WW) use DI instead
    public void insertSignupCode(String code, UserID from, UserID to, long currentTime)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_TI, C_TI_TIC, C_TI_FROM, C_TI_TO, C_TI_TS));

        ps.setString(1, code);
        ps.setString(2, from.toString());
        ps.setString(3, to.toString());
        ps.setTimestamp(4, new Timestamp(currentTime), UTC_CALANDER);
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

    public Collection<SID> getSharedFolders(UserID userId) throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_USER_ID + "=? and " + C_AC_PENDING + "=?",
                C_AC_STORE_ID));

        ps.setString(1, userId.toString());
        ps.setBoolean(2, false);

        ResultSet rs = ps.executeQuery();
        try {
            List<SID> sids = Lists.newArrayList();
            while (rs.next()) sids.add(new SID(rs.getBytes(1)));
            return sids;
        } finally {
            rs.close();
        }
    }

    public static class PendingSharedFolder
    {
        public final SID _sid;
        public final UserID _sharer;

        PendingSharedFolder(SID sid, UserID sharer)
        {
            _sid = sid;
            _sharer = sharer;
        }
    }

    // TODO (WW) move this method to SharedFolderDatabase?
    public Collection<PendingSharedFolder> getPendingSharedFolders(UserID userId) throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_USER_ID + "=? and " + C_AC_PENDING + "=?",
                C_AC_STORE_ID, C_AC_SHARER));

        ps.setString(1, userId.toString());
        ps.setBoolean(2, true);

        ResultSet rs = ps.executeQuery();
        try {
            List<PendingSharedFolder> sids = Lists.newArrayList();
            while (rs.next()) {
                sids.add(new PendingSharedFolder(new SID(rs.getBytes(1)),
                        UserID.fromInternal(rs.getString(2))));
            }
            return sids;
        } finally {
            rs.close();
        }
    }

    public Long incrementACLEpoch(UserID user) throws SQLException
    {
        PreparedStatement ps = prepareStatement("update " + T_USER +
                " set " + C_USER_ACL_EPOCH + "=" + C_USER_ACL_EPOCH + "+1" +
                " where " + C_USER_ID + "=?");

        ps.setString(1, user.toString());
        int rows = ps.executeUpdate();

        assert rows == 1 : user + " " + rows;

        return getACLEpoch(user);
    }

    public long getACLEpoch(UserID user) throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_USER, C_USER_ID + "=?",
                C_USER_ACL_EPOCH));
        return queryGetACLEpoch(ps, user);
    }

    private long queryGetACLEpoch(PreparedStatement ps, UserID user)
            throws SQLException
    {
        ps.setString(1, user.toString());
        ResultSet rs = ps.executeQuery();
        try {
            Util.verify(rs.next());
            long epoch = rs.getLong(1);
            assert !rs.next();
            return epoch;
        } finally {
            rs.close();
        }
    }
}
