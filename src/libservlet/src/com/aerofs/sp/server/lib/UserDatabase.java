/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.base.Base64;
import com.aerofs.lib.FullName;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

import static com.aerofs.lib.db.DBUtil.binaryCount;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STATE;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_SHARER;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STORE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_OWNER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_UNLINKED;
import static com.aerofs.sp.server.lib.SPSchema.C_SIGNUP_CODE_CODE;
import static com.aerofs.sp.server.lib.SPSchema.C_SIGNUP_CODE_TO;
import static com.aerofs.sp.server.lib.SPSchema.C_SIGNUP_CODE_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ACL_EPOCH;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_BYTES_USED;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_DEACTIVATED;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_AUTHORIZATION_LEVEL;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_CREDS;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_FIRST_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_LAST_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ORG_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_SIGNUP_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_USAGE_WARNING_SENT;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_WHITELISTED;
import static com.aerofs.sp.server.lib.SPSchema.T_AC;
import static com.aerofs.sp.server.lib.SPSchema.T_DEVICE;
import static com.aerofs.sp.server.lib.SPSchema.T_SIGNUP_CODE;
import static com.aerofs.sp.server.lib.SPSchema.T_USER;

/**
 * N.B. only User.java may refer to this class
 */
public class UserDatabase extends AbstractSQLDatabase
{
    @Inject
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
        // if the user was previously deactivated, reactivate it
        if (isDeactivated(id)) {
            reactivate(id, fullName, shaedSP, orgID, level);
            return;
        }

        // we always create a user with initial epoch + 1 to ensure that the first time
        // a device is created it gets any acl updates that were made while the user
        // didn't have an entry in the user table

        PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_USER, C_USER_ID, C_USER_CREDS, C_USER_FIRST_NAME,
                        C_USER_LAST_NAME, C_USER_ORG_ID, C_USER_AUTHORIZATION_LEVEL,
                        C_USER_ACL_EPOCH, C_USER_DEACTIVATED));

        ps.setString(1, id.getString());
        ps.setString(2, Base64.encodeBytes(shaedSP));
        ps.setString(3, fullName._first);
        ps.setString(4, fullName._last);
        ps.setInt(5, orgID.getInt());
        ps.setInt(6, level.ordinal());
        //noinspection PointlessArithmeticExpression
        ps.setInt(7, LibParam.INITIAL_ACL_EPOCH + 1);
        ps.setBoolean(8, false);

        try {
            ps.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "user " + id.getString() + " already exists");
            throw e;
        }
    }

    private boolean isDeactivated(UserID userId) throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=1",
                "count(*)"));
        ps.setString(1, userId.getString());

        return binaryCount(ps.executeQuery());
    }

    private void reactivate(UserID id, FullName fullName, byte[] shaedSP, OrganizationID orgID,
            AuthorizationLevel level) throws SQLException
    {
        PreparedStatement ps = prepareStatement(DBUtil.updateWhere(T_USER,
                C_USER_ID + "=?",
                C_USER_CREDS, C_USER_FIRST_NAME, C_USER_LAST_NAME, C_USER_ORG_ID,
                C_USER_AUTHORIZATION_LEVEL, C_USER_ACL_EPOCH, C_USER_DEACTIVATED));

        ps.setString(8, id.getString());
        ps.setString(1, Base64.encodeBytes(shaedSP));
        ps.setString(2, fullName._first);
        ps.setString(3, fullName._last);
        ps.setInt(4, orgID.getInt());
        ps.setInt(5, level.ordinal());
        //noinspection PointlessArithmeticExpression
        ps.setInt(6, LibParam.INITIAL_ACL_EPOCH + 1);
        ps.setBoolean(7, false);

        Util.verify(ps.executeUpdate() == 1);
    }

    public boolean hasUser(UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0", "count(*)"));
        ps.setString(1, userId.getString());
        ResultSet rs = ps.executeQuery();
        try {
            return DBUtil.binaryCount(rs);
        } finally {
            rs.close();
        }
    }

    public boolean hasUsers()
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_USER,
                C_USER_DEACTIVATED + "=0",
                "count(*)"));
        ResultSet rs = ps.executeQuery();
        try {
            return DBUtil.count(rs) > 0;
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
        PreparedStatement ps = prepareStatement(updateWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0",
                C_USER_ORG_ID));

        ps.setInt(1, orgId.getInt());
        ps.setString(2, userId.getString());
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

    public long getSignupDate(UserID userId)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryUser(userId, C_USER_SIGNUP_TS);
        try {
            return rs.getTimestamp(1).getTime();
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

    public boolean isWhitelisted(UserID userID)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryUser(userID, C_USER_WHITELISTED);
        try {
            return rs.getBoolean(1);
        } finally {
            rs.close();
        }
    }

    public boolean getUsageWarningSent(UserID userID)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryUser(userID, C_USER_USAGE_WARNING_SENT);
        try {
            return rs.getBoolean(1);
        } finally {
            rs.close();
        }
    }

    public void setUsageWarningSent(UserID userID, boolean usageWarningSent)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                updateWhere(T_USER, C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0",
                        C_USER_USAGE_WARNING_SENT));

        ps.setBoolean(1, usageWarningSent);
        ps.setString(2, userID.getString());
        ps.executeUpdate();
    }

    /**
     * @return the usage in bytes, or null if the value has never been set
     */
    public @Nullable Long getBytesUsed(UserID userID)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryUser(userID, C_USER_BYTES_USED);
        try {
            Long bytesUsed = rs.getLong(1);
            if (rs.wasNull()) return null;
            return bytesUsed;
        } finally {
            rs.close();
        }
    }

    public void setBytesUsed(UserID userID, long bytesUsed)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                updateWhere(T_USER, C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0",
                        C_USER_BYTES_USED));

        ps.setLong(1, bytesUsed);
        ps.setString(2, userID.getString());
        ps.executeUpdate();
    }

    /**
     * List all devices belonging to a the provided user.
     */
    public ImmutableList<DID> getDevices(UserID userId)
            throws SQLException, ExFormatError
    {
        PreparedStatement ps = prepareStatement(
                selectWhere(T_DEVICE, C_DEVICE_OWNER_ID + "=? and " + C_DEVICE_UNLINKED + "=0",
                        C_DEVICE_ID));

        ps.setString(1, userId.getString());

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

    private ResultSet queryUser(UserID userId, String ... fields)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0", fields));
        ps.setString(1, userId.getString());
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            throw new ExNotFound("user " + userId + " is not found");
        } else {
            return rs;
        }
    }

    public void setLevel(UserID userId, AuthorizationLevel authLevel)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0",
                C_USER_AUTHORIZATION_LEVEL));

        ps.setInt(1, authLevel.ordinal());
        ps.setString(2, userId.getString());
        Util.verify(ps.executeUpdate() == 1);
    }

    public void setWhitelisted(UserID userID, boolean whitelisted)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0", C_USER_WHITELISTED));

        ps.setBoolean(1, whitelisted);
        ps.setString(2, userID.getString());
        ps.executeUpdate();
    }

    public void setName(UserID userId, FullName fullName)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_USER,
                C_USER_ID + "=? and " + C_USER_DEACTIVATED + "=0",
                C_USER_FIRST_NAME, C_USER_LAST_NAME));

        // TODO (WW) instead of doing trim here, normalize the FullName at entry points.
        // See UserID.fromInternal/fromExternal
        ps.setString(1, fullName._first.trim());
        ps.setString(2, fullName._last.trim());
        ps.setString(3, userId.getString());
        Util.verify(ps.executeUpdate() == 1);
    }

    // TODO (WW) move it to a different database class?
    public void insertSignupCode(String code, UserID to)
            throws SQLException
    {
        insertSignupCode(code, to, System.currentTimeMillis());
    }

    // For testing only
    // TODO (WW) use DI instead
    public void insertSignupCode(String code, UserID to, long currentTime)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_SIGNUP_CODE, C_SIGNUP_CODE_CODE, C_SIGNUP_CODE_TO, C_SIGNUP_CODE_TS));

        ps.setString(1, code);
        ps.setString(2, to.getString());
        ps.setTimestamp(3, new Timestamp(currentTime), UTC_CALENDAR);
        ps.executeUpdate();
    }

    /**
     * N.B. We don't currently have an index on C_SIGNUP_CODE_TO since sign-up code deletion is not
     * expected to be a frequent operation. Add the index in the future if needed.
     */
    public void deleteAllSignUpCodes(UserID userID)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.deleteWhere(T_SIGNUP_CODE, C_SIGNUP_CODE_TO + "=?"));

        ps.setString(1, userID.getString());
        ps.executeUpdate();
    }

    public Collection<SID> getSharedFolders(UserID userId) throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_USER_ID + "=? and " + C_AC_STATE + "=?",
                C_AC_STORE_ID));

        ps.setString(1, userId.getString());
        ps.setInt(2, SharedFolderState.JOINED.ordinal());

        ResultSet rs = ps.executeQuery();
        try {
            List<SID> sids = Lists.newArrayList();
            while (rs.next()) sids.add(new SID(rs.getBytes(1)));
            return sids;
        } finally {
            rs.close();
        }
    }

    /**
     * Deactivate a user
     *
     * NB: the system cannot currently (and maybe ever) gracefully deal with deletion for
     * user/device information. This is mostly because the tick space is append-only: new
     * ticks can be added but old ticks cannot ever be removed and some other features,
     * most notably activity log, extract user-visible info from ticks and need to associate
     * this info to user and device names.
     */
    public void deactivate(UserID userId) throws SQLException
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_USER,
                C_USER_ID + "=?", C_USER_DEACTIVATED));

        ps.setBoolean(1, true);
        ps.setString(2, userId.getString());

        Util.verify(ps.executeUpdate() == 1);
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
                C_AC_USER_ID + "=? and " + C_AC_STATE + "=?",
                C_AC_STORE_ID, C_AC_SHARER));

        ps.setString(1, userId.getString());
        ps.setInt(2, SharedFolderState.PENDING.ordinal());

        ResultSet rs = ps.executeQuery();
        try {
            List<PendingSharedFolder> sids = Lists.newArrayList();
            while (rs.next()) {
                String sharer = rs.getString(2);
                // TODO (WW) this is a temporary hack: It's true by definition that a pending user
                // always has a sharer. However, because of the way the system is currently
                // implemented, a member without a sharer (e.g. the initial owner of the folder) can
                // be converted to pending when he/she deletes the folder. Once the three-state
                // system is implemented, the following statement should be changed to throwing
                // an internal system error instead.
                if (rs.wasNull()) sharer = userId.getString();

                sids.add(new PendingSharedFolder(new SID(rs.getBytes(1)),
                        UserID.fromInternal(sharer)));
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

        ps.setString(1, user.getString());
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
        ps.setString(1, user.getString());
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
