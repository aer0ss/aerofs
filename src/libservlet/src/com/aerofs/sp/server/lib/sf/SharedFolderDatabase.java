/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.sf;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.GroupID;
import static com.aerofs.base.id.GroupID.NULL_GROUP;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.aerofs.sp.common.SharedFolderState;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableCollection.Builder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import static com.aerofs.lib.db.DBUtil.binaryCount;
import static com.aerofs.lib.db.DBUtil.count;
import static com.aerofs.lib.db.DBUtil.deleteWhere;
import static com.aerofs.lib.db.DBUtil.insertOnDuplicateUpdate;
import static com.aerofs.lib.db.DBUtil.insertedOrUpdatedOneRow;
import static com.aerofs.lib.db.DBUtil.selectDistinctWhere;
import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_EXTERNAL;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_GID;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_ROLE;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_SHARER;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STATE;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_STORE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SFN_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_SFN_STORE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SFN_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_SF_ORIGINAL_NAME;
import static com.aerofs.sp.server.lib.SPSchema.T_AC;
import static com.aerofs.sp.server.lib.SPSchema.T_SF;
import static com.aerofs.sp.server.lib.SPSchema.T_SFN;

/**
 * N.B. only User.java may refer to this class
 */
public class SharedFolderDatabase extends AbstractSQLDatabase
{
    @Inject
    public SharedFolderDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public boolean has(SID sid) throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_SF, C_SF_ID + "=?", "count(*)"))) {

            ps.setBytes(1, sid.getBytes());
            try (ResultSet rs = ps.executeQuery()) {
                return binaryCount(rs);
            }
        }
    }

    /**
     * Add the given sid to the shared folder table
     */
    public void insert(SID sid, String name)
            throws SQLException, ExAlreadyExist
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.insert(T_SF, C_SF_ID, C_SF_ORIGINAL_NAME))) {

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
    }

    public void insertUser(SID sid, UserID user, Permissions permissions, SharedFolderState state,
            @Nullable UserID sharer, GroupID gid)
            throws SQLException, ExAlreadyExist
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.insert(T_AC, C_AC_STORE_ID, C_AC_USER_ID,
                C_AC_ROLE, C_AC_STATE, C_AC_SHARER, C_AC_GID))) {

            ps.setBytes(1, sid.getBytes());
            ps.setString(2, user.getString());
            ps.setInt(3, permissions.bitmask());
            ps.setInt(4, state.ordinal());
            if (sharer != null) {
                ps.setString(5, sharer.getString());
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.setInt(6, gid.getInt());

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
                throwOnConstraintViolation(e, "the (user, group) already exists in shared folder");
                throw e;
            }
        }
    }

    public void setState(SID sid, UserID userId, SharedFolderState state)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(
                updateWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_STATE))) {

            ps.setInt(1, state.ordinal());
            ps.setBytes(2, sid.getBytes());
            ps.setString(3, userId.getString());

            if (ps.executeUpdate() == 0) throw new ExNotFound();
        }
    }

    // see docs/design/sharing_and_migration.txt for information about the external flag
    public void setExternal(SID sid, UserID userId, boolean external)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_EXTERNAL))) {

            ps.setBoolean(1, external);
            ps.setBytes(2, sid.getBytes());
            ps.setString(3, userId.getString());

            if (ps.executeUpdate() == 0) throw new ExNotFound();
        }
    }

    // see docs/design/sharing_and_migration.txt for information about the external flag
    public boolean isExternal(SID sid, UserID id) throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_EXTERNAL))) {

            ps.setBytes(1, sid.getBytes());
            ps.setString(2, id.getString());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    @Nullable Permissions getEffectiveRoleNullable(SID sid, UserID userId)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_USER_ID +
                "=? group by " + C_AC_USER_ID, effectiveRole()))) {

            ps.setBytes(1, sid.getBytes());
            ps.setString(2, userId.getString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { // there is no entry in the ACL table for this {storeid, userid}
                    return null;
                } else {
                    Permissions permissions = Permissions.fromBitmask(rs.getInt(1));
                    assert !rs.next();
                    return permissions;
                }
            }
        }
    }

    public @Nullable Permissions getRoleNullable(SID sid, UserID userId, GroupID gid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_USER_ID +
                "=? and " + C_AC_GID + "=?", C_AC_ROLE))) {

            ps.setBytes(1, sid.getBytes());
            ps.setString(2, userId.getString());
            ps.setInt(3, gid.getInt());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { // there is no entry in the ACL table for this {storeid, userid, gid}
                    return null;
                } else {
                    Permissions permissions = Permissions.fromBitmask(rs.getInt(1));
                    assert !rs.next();
                    return permissions;
                }
            }
        }
    }

    public @Nullable SharedFolderState getStateNullable(SID sid, UserID userId)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectDistinctWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?", C_AC_STATE))) {

            ps.setBytes(1, sid.getBytes());
            ps.setString(2, userId.getString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                } else {
                    SharedFolderState state = SharedFolderState.fromOrdinal(rs.getInt(1));
                    // should only be one row here, since we're selecting distinct values
                    assert !rs.next();
                    return state;
                }
            }
        }
    }

    public @Nullable UserID getSharerNullable(SID sid, UserID userId)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=? and " + C_AC_SHARER + " is not null",
                C_AC_SHARER))) {

            ps.setBytes(1, sid.getBytes());
            ps.setString(2, userId.getString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // TODO (RD): if sharer is null, might be because user was added to group and invited
                    // to its shared folders - can either return the group in this case or propagate the
                    // original sharer of the folder with the group
                    return null;
                } else {
                    return UserID.fromInternal(rs.getString(1));
                }
            }
        }
    }

    public ImmutableCollection<UserID> getJoinedUsers(SID sid) throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(
                selectDistinctWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_STATE + "=" +
                        SharedFolderState.JOINED.ordinal(), C_AC_USER_ID))) {

            ps.setBytes(1, sid.getBytes());

            try (ResultSet rs = ps.executeQuery()) {
                Builder<UserID> users = ImmutableList.builder();
                while (rs.next()) users.add(UserID.fromInternal(rs.getString(1)));
                return users.build();
            }
        }
    }

    /**
     * @return all the users including Team Servers
     */
    public ImmutableCollection<UserID> getAllUsers(SID sid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(
                selectDistinctWhere(T_AC, C_AC_STORE_ID + "=?", C_AC_USER_ID))) {

            ps.setBytes(1, sid.getBytes());

            try (ResultSet rs = ps.executeQuery()) {
                Builder<UserID> users = ImmutableList.builder();
                while (rs.next()) users.add(UserID.fromInternal(rs.getString(1)));
                return users.build();
            }
        }
    }

    public ImmutableMap<UserID, Permissions> getJoinedUsersAndRoles(SID sid) throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_AC, C_AC_STORE_ID + "=? and " +
                        C_AC_STATE + "=" + SharedFolderState.JOINED.ordinal() +
                        " group by " + C_AC_USER_ID, C_AC_USER_ID, effectiveRole()))) {

            ps.setBytes(1, sid.getBytes());

            try (ResultSet rs = ps.executeQuery()) {
                ImmutableMap.Builder<UserID, Permissions> builder = ImmutableMap.builder();
                while (rs.next()) {
                    UserID userID = UserID.fromInternal(rs.getString(1));
                    Permissions permissions = Permissions.fromBitmask(rs.getInt(2));

                    builder.put(userID, permissions);
                }
                return builder.build();
            }
        }
    }

    public Iterable<UserIDRoleAndState> getUserRolesAndStatesWithGroup(SID sid, GroupID gid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_AC, C_AC_STORE_ID + "=? and " + C_AC_GID
                + "=?", C_AC_USER_ID, C_AC_ROLE, C_AC_STATE))) {
            ps.setBytes(1, sid.getBytes());
            ps.setInt(2, gid.getInt());

            try (ResultSet rs = ps.executeQuery()) {
                ImmutableList.Builder<UserIDRoleAndState> builder =
                        ImmutableList.builder();

                while (rs.next()) {
                    UserID userID = UserID.fromInternal(rs.getString(1));
                    Permissions permissions = Permissions.fromBitmask(rs.getInt(2));
                    SharedFolderState state = SharedFolderState.fromOrdinal(rs.getInt(3));

                    builder.add(new UserIDRoleAndState(userID, permissions, state));
                }
                return builder.build();
            }
        }
    }

    public Iterable<UserIDRoleAndState> getAllUsersRolesAndStates(SID sid)
            throws SQLException
    {
        // group the rows by user ID and state so we can compute the effective roles in the DB
        // using a bitwise_or aggregate
        // TODO (RD): verify that there is only one state per UserID
        try (PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? group by " + C_AC_USER_ID + ", " + C_AC_STATE,
                C_AC_USER_ID, effectiveRole(), C_AC_STATE))) {

            ps.setBytes(1, sid.getBytes());

            try (ResultSet rs = ps.executeQuery()) {
                ImmutableList.Builder<UserIDRoleAndState> builder = ImmutableList.builder();

                while (rs.next()) {
                    UserID userID = UserID.fromInternal(rs.getString(1));
                    Permissions permissions = Permissions.fromBitmask(rs.getInt(2));
                    SharedFolderState state = SharedFolderState.fromOrdinal(rs.getInt(3));

                    builder.add(new UserIDRoleAndState(userID, permissions, state));
                }
                return builder.build();
            }
        }
    }

    public void delete(SID sid, UserID userID, GroupID gid)
            throws ExNotFound, SQLException
    {
        try (PreparedStatement ps = prepareStatement(deleteWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=? and " + C_AC_GID + " =?"))) {

            ps.setBytes(1, sid.getBytes());
            ps.setString(2, userID.getString());
            ps.setInt(3, gid.getInt());
            if (ps.executeUpdate() != 1) throw new ExNotFound();
        }
    }

    private static String hasPermission(Permission permission)
    {
        return "(" + C_AC_ROLE + "&" + permission.flag() + ")!=0";
    }

    public boolean hasOwner(SID sid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_AC,
                C_AC_STORE_ID + "=? and " + hasPermission(Permission.MANAGE), "count(*)"))) {

            ps.setBytes(1, sid.getBytes());

            try (ResultSet rs = ps.executeQuery()) {
                return count(rs) != 0;
            }
        }
    }

    public void setPermissions(SID sid, UserID userID, Permissions permissions)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=? and " + C_AC_GID + "=?",
                C_AC_ROLE))) {

            ps.setInt(1, permissions.bitmask());
            ps.setBytes(2, sid.getBytes());
            ps.setString(3, userID.getString());
            ps.setInt(4, NULL_GROUP.getInt());

            if (ps.executeUpdate() != 1) throw new ExNotFound();
        }
    }

    public void setPermissionsForGroup(SID sid, GroupID gid, Permissions permissions)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(updateWhere(T_AC,
                C_AC_STORE_ID + "=? and " + C_AC_GID + "=?",
                C_AC_ROLE))) {

            ps.setInt(1, permissions.bitmask());
            ps.setBytes(2, sid.getBytes());
            ps.setInt(3, gid.getInt());

            ps.executeUpdate();
        }
    }

    public void grantPermission(SID sid, UserID userID, Permission permission)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement("update " + T_AC
                + " set " + C_AC_ROLE + "=" + C_AC_ROLE + " | ?"
                + " where " +  C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=? and "
                + C_AC_GID + "=?")) {

            ps.setInt(1, permission.flag());
            ps.setBytes(2, sid.getBytes());
            ps.setString(3, userID.getString());
            ps.setInt(4, NULL_GROUP.getInt());

            if (ps.executeUpdate() != 1) throw new ExNotFound();
        }
    }

    // N.B. this method revokes permissions for a user across all their ACLs, including groups
    // it's only used by restricted external sharing, where it would be ineffectual to do otherwise
    public void revokePermission(SID sid, UserID userID, Permission permission)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement("update " + T_AC
                + " set " + C_AC_ROLE + "=" + C_AC_ROLE + " & ?"
                + " where " +  C_AC_STORE_ID + "=? and " + C_AC_USER_ID + "=?")) {

            ps.setInt(1, ~permission.flag());
            ps.setBytes(2, sid.getBytes());
            ps.setString(3, userID.getString());

            if (ps.executeUpdate() == 0) throw new ExNotFound();
        }
    }

    public void destroy(SID sid)
            throws SQLException
    {
        // remove all ACLs
        try (PreparedStatement ps = prepareStatement(DBUtil.deleteWhere(T_AC, C_AC_STORE_ID + "=?"))) {
            ps.setBytes(1, sid.getBytes());
            ps.executeUpdate();
        }

        // remove shared folder
        try (PreparedStatement ps = prepareStatement(DBUtil.deleteWhere(T_SF, C_SF_ID + "=?"))) {
            ps.setBytes(1, sid.getBytes());
            Util.verify(ps.executeUpdate() > 0);
        }
    }

    public @Nonnull String getName(SID sid, @Nullable UserID userID)
            throws SQLException, ExNotFound
    {
        // Return the user-specified name, if any
        String name = userID != null ? getUserSpecifiedName(sid, userID) : null;
        if (name != null) return name;

        // Otherwise, return the original name
        try (PreparedStatement ps = querySharedFolder(sid, C_SF_ORIGINAL_NAME);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, sid);
            return rs.getString(1);
        }
    }

    public void setName(SID sid, UserID userID, String name)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(insertOnDuplicateUpdate(T_SFN, C_SFN_NAME + "=?",
                C_SFN_STORE_ID, C_SFN_USER_ID, C_SFN_NAME))) {

            ps.setBytes(1, sid.getBytes());
            ps.setString(2, userID.getString());
            ps.setString(3, name);
            ps.setString(4, name);

            int result = ps.executeUpdate();
            Util.verify(insertedOrUpdatedOneRow(result));
        }
    }

    /**
     * Given an sid and a user id, returns the user-specified name for that shared folder, if any.
     * Returns null if the user didn't set a specific name for that shared folder yet.
     */
    private @Nullable String getUserSpecifiedName(SID sid, UserID userID)
            throws SQLException
    {
        String condition = C_SFN_STORE_ID + "=? and " + C_SFN_USER_ID + "=?";
        try (PreparedStatement ps = prepareStatement(selectWhere(T_SFN, condition, C_SFN_NAME))) {
            ps.setBytes(1, sid.getBytes());
            ps.setString(2, userID.getString());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private PreparedStatement querySharedFolder(SID sid, String field)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_SF, C_SF_ID + "=?", field));
        ps.setBytes(1, sid.getBytes());
        return ps;
    }

    // N.B. will return with cursor on the first element of the result set
    private void throwIfEmptyResultSet(ResultSet rs, SID sid)
            throws SQLException, ExNotFound
    {
        if (!rs.next()) {
            throw new ExNotFound("shared folder " + sid);
        }
    }

    private String effectiveRole()
    {
        return "BIT_OR(" + C_AC_ROLE + ")";
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
