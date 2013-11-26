/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.aerofs.sp.common.SharedFolderState;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableCollection.Builder;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import static com.aerofs.lib.db.DBUtil.binaryCount;
import static com.aerofs.lib.db.DBUtil.count;
import static com.aerofs.lib.db.DBUtil.deleteWhere;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_EXTERNAL;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STATE;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_ROLE;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_SHARER;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STORE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_NAME;
import static com.aerofs.sp.server.lib.SPSchema.T_AC;
import static com.aerofs.sp.server.lib.SPSchema.T_SF;

/**
 * N.B. only User.java may refer to this class
 */
public class SharedFolderDatabase extends AbstractSQLDatabase
{
    public SharedFolderDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public boolean has(SID sid) throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_SF, C_SF_ID + "=?", "count(*)"));

        ps.setBytes(1, sid.getBytes());
        ResultSet rs = ps.executeQuery();
        try {
            return binaryCount(rs);
        } finally {
            rs.close();
        }
    }

    /**
     * Add the given sid to the shared folder table
     */
    public void insert(SID sid, String name)
            throws SQLException, ExAlreadyExist
    {
        PreparedStatement ps = prepareStatement(DBUtil.insert(T_SF, C_SF_ID, C_SF_NAME));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, name);

        // Update returns 1 on successful insert
        try {
            Util.verify(ps.executeUpdate() == 1);
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "shared folder ID already exists");
            throw e;
        }
    }

    public void insertUser(SID sid, UserID user, Permissions permissions, SharedFolderState state,
            @Nullable UserID sharer)
            throws SQLException, ExAlreadyExist
    {
        PreparedStatement ps = prepareStatement(DBUtil.insert(T_AC, C_AC_STORE_ID, C_AC_USER_ID,
                C_AC_ROLE, C_AC_STATE, C_AC_SHARER));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, user.getString());
        ps.setInt(3, permissions.bitmask());
        ps.setInt(4, state.ordinal());
        if (sharer != null) {
            ps.setString(5, sharer.getString());
        } else {
            ps.setNull(5, Types.VARCHAR);
        }

        /**
         * We enforce a strict API distinction between ACL creation and ACL update
         * To ensure that SP calls are not abused (i.e shareFolder should not be used to change
         * existing permissions and updateACL should not give access to new users (as it would
         * leave the DB in an intermediate state where users have access to a folder but did not
         * receive an email about it)
         */
        try {
            Util.verify(ps.executeUpdate() == 1);
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "the user already exists in shared folder");
            throw e;
        }
    }

    public void setState(SID sid, UserID userId, SharedFolderState state)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(
                updateWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_STATE));

        ps.setInt(1, state.ordinal());
        ps.setBytes(2, sid.getBytes());
        ps.setString(3, userId.getString());

        if (ps.executeUpdate() != 1) throw new ExNotFound();
    }

    // see docs/design/sharing_and_migration.txt for information about the external flag
    public void setExternal(SID sid, UserID userId, boolean external)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_EXTERNAL));

        ps.setBoolean(1, external);
        ps.setBytes(2, sid.getBytes());
        ps.setString(3, userId.getString());

        if (ps.executeUpdate() != 1) throw new ExNotFound();
    }

    // see docs/design/sharing_and_migration.txt for information about the external flag
    public boolean isExternal(SID sid, UserID id) throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_EXTERNAL));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, id.getString());

        ResultSet rs = ps.executeQuery();
        try {
            return rs.next() && rs.getBoolean(1);
        } finally {
            rs.close();
        }
    }

    public @Nullable
    Permissions getRoleNullable(SID sid, UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_ROLE));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, userId.getString());

        ResultSet rs = ps.executeQuery();
        try {
            if (!rs.next()) { // there is no entry in the ACL table for this storeid/userid
                return null;
            } else {
                Permissions userPermissions = Permissions.fromBitmask(rs.getInt(1));
                assert !rs.next();
                return userPermissions;
            }
        } finally {
            rs.close();
        }
    }

    public @Nullable SharedFolderState getStateNullable(SID sid, UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_STATE));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, userId.getString());

        ResultSet rs = ps.executeQuery();
        try {
            if (!rs.next()) { // there is no entry in the ACL table for this storeid/userid
                return null;
            } else {
                SharedFolderState state = SharedFolderState.fromOrdinal(rs.getInt(1));
                assert !rs.next();
                return state;
            }
        } finally {
            rs.close();
        }
    }

    @Nullable
    public UserID getSharerNullable(SID sid, UserID userId)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_SHARER));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, userId.getString());

        ResultSet rs = ps.executeQuery();
        try {
            if (!rs.next()) {
                return null;
            } else {
                String sharer = rs.getString(1);
                assert !rs.next();
                return sharer == null ? null : UserID.fromInternal(sharer);
            }
        } finally {
            rs.close();
        }
    }

    public ImmutableCollection<UserID> getJoinedUsers(SID sid) throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                selectWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_STATE + "=" +
                        SharedFolderState.JOINED.ordinal(), C_AC_USER_ID));

        ps.setBytes(1, sid.getBytes());

        ResultSet rs = ps.executeQuery();
        try {
            Builder<UserID> users = ImmutableList.builder();
            while (rs.next()) users.add(UserID.fromInternal(rs.getString(1)));
            return users.build();
        } finally {
            rs.close();
        }
    }

    /**
     * @return all the users including Team Servers
     */
    public ImmutableCollection<UserID> getAllUsers(SID sid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                selectWhere(T_AC, C_AC_STORE_ID + "=?", C_AC_USER_ID));

        ps.setBytes(1, sid.getBytes());

        ResultSet rs = ps.executeQuery();
        try {
            Builder<UserID> users = ImmutableList.builder();
            while (rs.next()) users.add(UserID.fromInternal(rs.getString(1)));
            return users.build();
        } finally {
            rs.close();
        }
    }

    public Iterable<SubjectPermissions> getJoinedUsersAndRoles(SID sid) throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_STATE + "=?", C_AC_USER_ID, C_AC_ROLE));

        ps.setBytes(1, sid.getBytes());
        ps.setInt(2, SharedFolderState.JOINED.ordinal());

        ResultSet rs = ps.executeQuery();
        try {
            ImmutableList.Builder<SubjectPermissions> builder = ImmutableList.builder();
            while (rs.next()) {
                UserID userID = UserID.fromInternal(rs.getString(1));
                Permissions permissions = Permissions.fromBitmask(rs.getInt(2));

                builder.add(new SubjectPermissions(userID, permissions));
            }
            return builder.build();
        } finally {
            rs.close();
        }
    }

    public Iterable<UserIDRoleAndState> getAllUsersRolesAndStates(SID sid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC, C_AC_STORE_ID + "=?",
                C_AC_USER_ID, C_AC_ROLE, C_AC_STATE));

        ps.setBytes(1, sid.getBytes());

        ResultSet rs = ps.executeQuery();
        try {
            ImmutableList.Builder<UserIDRoleAndState> builder =
                    ImmutableList.builder();

            while (rs.next()) {
                UserID userID = UserID.fromInternal(rs.getString(1));
                Permissions permissions = Permissions.fromBitmask(rs.getInt(2));
                SharedFolderState state = SharedFolderState.fromOrdinal(rs.getInt(3));

                builder.add(new UserIDRoleAndState(userID, permissions, state));
            }
            return builder.build();
        } finally {
            rs.close();
        }
    }

    public void delete(SID sid, UserID userID)
            throws ExNotFound, SQLException
    {
        PreparedStatement ps = prepareStatement(
                deleteWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?"));

        ps.setBytes(1, sid.getBytes());
        ps.setString(2, userID.getString());
        if (ps.executeUpdate() != 1) throw new ExNotFound();
    }

    private static String hasPermission(Permission permission)
    {
        return "(" + C_AC_ROLE + "&" + permission.flag() + ")!=0";
    }

    public boolean hasOwner(SID sid)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + hasPermission(Permission.MANAGE), "count(*)"));

        ps.setBytes(1, sid.getBytes());

        ResultSet rs = ps.executeQuery();
        try {
            return count(rs) != 0;
        } finally {
            rs.close();
        }
    }

    public void setPermissions(SID sid, UserID userID, Permissions permissions)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(updateWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_ROLE));

        ps.setInt(1, permissions.bitmask());
        ps.setBytes(2, sid.getBytes());
        ps.setString(3, userID.getString());

        if (ps.executeUpdate() != 1) throw new ExNotFound();
    }

    public void grantPermission(SID sid, UserID userID, Permission permission)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement("update " + T_AC
                + " set " + C_AC_ROLE + "=" + C_AC_ROLE + " | ?"
                + " where " +  C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?");

        ps.setInt(1, permission.flag());
        ps.setBytes(2, sid.getBytes());
        ps.setString(3, userID.getString());

        if (ps.executeUpdate() != 1) throw new ExNotFound();
    }

    public void revokePermission(SID sid, UserID userID, Permission permission)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement("update " + T_AC
                + " set " + C_AC_ROLE + "=" + C_AC_ROLE + " & ?"
                + " where " +  C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?");

        ps.setInt(1, ~permission.flag());
        ps.setBytes(2, sid.getBytes());
        ps.setString(3, userID.getString());

        if (ps.executeUpdate() != 1) throw new ExNotFound();
    }

    public void destroy(SID sid)
            throws SQLException
    {
        PreparedStatement ps;

        // remove all ACLs
        ps = prepareStatement(DBUtil.deleteWhere(T_AC, C_AC_STORE_ID + "=?"));
        ps.setBytes(1, sid.getBytes());
        ps.executeUpdate();

        // remove shared folder
        ps = prepareStatement(DBUtil.deleteWhere(T_SF, C_SF_ID + "=?"));
        ps.setBytes(1, sid.getBytes());
        Util.verify(ps.executeUpdate() > 0);
    }

    public @Nonnull String getName(SID sid)
            throws SQLException, ExNotFound
    {
        ResultSet rs = querySharedFolder(sid, C_SF_NAME);
        try {
            return rs.getString(1);
        } finally {
            rs.close();
        }
    }

    private ResultSet querySharedFolder(SID sid, String field)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_SF, C_SF_ID + "=?", field));
        ps.setBytes(1, sid.getBytes());
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            throw new ExNotFound("shared folder " + sid);
        } else {
            return rs;
        }
    }

    // the only purpose of this class is to carry data from getAllUsersRolesAndStates()
    public static class UserIDRoleAndState
    {
        @Nonnull public final UserID _userID;
        @Nonnull public final Permissions _permissions;
        @Nonnull public final SharedFolderState _state;

        public UserIDRoleAndState(@Nonnull UserID userID, @Nonnull Permissions permissions,
                @Nonnull SharedFolderState state)
        {
            _userID = userID;
            _permissions = permissions;
            _state = state;
        }

        @Override
        public boolean equals(Object that)
        {
            return this == that ||
                    (that instanceof UserIDRoleAndState &&
                            _userID.equals(((UserIDRoleAndState)that)._userID) &&
                            _permissions.equals(((UserIDRoleAndState)that)._permissions) &&
                            _state.equals(((UserIDRoleAndState)that)._state));
        }

        @Override
        public int hashCode()
        {
            return _userID.hashCode() ^ _permissions.hashCode() ^ _state.hashCode();
        }
    }
}
