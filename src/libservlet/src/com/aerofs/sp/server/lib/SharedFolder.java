/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.base.ParamFactory;
import com.aerofs.sp.server.lib.SharedFolderDatabase.UserIDRoleAndState;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLException;

import static com.aerofs.sp.common.SharedFolderState.JOINED;
import static com.aerofs.sp.common.SharedFolderState.PENDING;
import static com.google.common.base.Preconditions.checkArgument;

public class SharedFolder
{
    public static class Factory
    {
        private SharedFolderDatabase _db;
        private User.Factory _factUser;

        @Inject
        public void inject(SharedFolderDatabase db, User.Factory factUser)
        {
            _db = db;
            _factUser = factUser;
        }

        public SharedFolder create(ByteString sid)
        {
            return create(new SID(sid));
        }

        public SharedFolder create(SID sid)
        {
            return new SharedFolder(this, sid);
        }

        @ParamFactory
        public SharedFolder _create(String s)
        {
            try {
                return create(new SID(s));
            } catch (ExFormatError e) {
                throw new IllegalArgumentException("Invalid SID");
            }
        }
    }

    private final Factory _f;
    private final SID _sid;

    private SharedFolder(Factory f, SID sid)
    {
        _f = f;
        _sid = sid;
    }

    public SID id()
    {
        return _sid;
    }

    @Override
    public int hashCode()
    {
        return _sid.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return o == this || (o != null && (((SharedFolder)o)._sid.equals(_sid)));
    }

    @Override
    public String toString()
    {
        return "shared folder " + _sid.toString();
    }

    public boolean exists()
            throws SQLException
    {
        return _f._db.has(_sid);
    }

    /**
     * Return the name of this shared folder for a given user. If the user hasn't set a specific
     * name yet, the original name (the one set when the folder was created) is returned.
     *
     * Note that it is ok to call this method with a non-existent user or a user that has no
     * relationship with this folder. The original shared folder name will be returned.
     *
     * @param user user for which we want to get this shared folder's name.
     * @throws ExNotFound if this shared folder isn't found in the db.
     */
    public String getName(User user)
            throws ExNotFound, SQLException
    {
        return _f._db.getName(_sid, user.id());
    }

    /**
     * Set the name of this shared folder for a given user.
     *
     * @param user the user for which we want to set the name. Must be an existing user, but it
     * doesn't have to have any kind of relationship with this shared folder.
     * @param name name of this shared folder. Cannot be empty.
     *
     * @throws ExNotFound if this shared folder or the user doesn't exist.
     * @throws IllegalArgumentException if name is an empty string.
     * @throws SQLException
     */
    public void setName(User user, String name)
            throws SQLException, ExNotFound
    {
        if (!exists()) throw new ExNotFound("shared folder " + _sid);
        if (!user.exists()) throw new ExNotFound("user " + user);
        checkArgument(!name.isEmpty(), "name cannot be emtpy");

        _f._db.setName(_sid, user.id(), name);
    }

    /**
     * Add the shared folder to db. Also add {@code owner} as the first owner.
     * @return A map of user IDs to epochs to be published via verkehr.
     * @throws com.aerofs.base.ex.ExAlreadyExist if the store already exists
     * @throws com.aerofs.base.ex.ExNotFound if the owner is not found
     */
    public ImmutableCollection<UserID> save(String folderName, User owner)
            throws ExNotFound, SQLException, ExAlreadyExist
    {
        _f._db.insert(_sid, folderName);

        try {
            return addJoinedUser(owner, Permissions.OWNER);
        } catch (ExAlreadyExist e) {
            throw SystemUtil.fatal(e);
        }
    }

    /**
     * Delete the folder completely from the universe. It will cause the folder to disappear from
     * all the devices.
     * @return A set of user IDs for which epoch should be increased and published via verkehr
     */
    public ImmutableCollection<UserID> destroy()
            throws SQLException
    {
        ImmutableCollection<UserID> joined = getJoinedUserIDs();
        _f._db.destroy(_sid);
        return joined;
    }

    /**
     * @return A set of user IDs for which epoch should be increased and published via verkehr
     * @throws com.aerofs.base.ex.ExAlreadyExist if the user is already added.
     */
    public ImmutableCollection<UserID> addJoinedUser(User user, Permissions permissions)
            throws ExAlreadyExist, SQLException, ExNotFound
    {
        _f._db.insertUser(_sid, user.id(), permissions, JOINED, null);

        addTeamServerForUserImpl(user);

        return getJoinedUserIDs();
    }

    public void addPendingUser(User user, Permissions permissions, User sharer)
            throws SQLException, ExAlreadyExist
    {
        _f._db.insertUser(_sid, user.id(), permissions, PENDING, sharer.id());
    }

    public ImmutableCollection<UserID> setState(User user, SharedFolderState newState)
            throws SQLException, ExNotFound, ExAlreadyExist
    {
        boolean wasJoined = getStateNullable(user) == JOINED;

        ImmutableCollection <UserID> affectedUsers;
        if (newState == JOINED) {
            // adding the reference to TS if the user joins
            if (!wasJoined) {
                _f._db.setState(_sid, user.id(), newState);
                addTeamServerForUserImpl(user);

                // retrieve the affected users _after_ the user's state is changed
                affectedUsers = getJoinedUserIDs();
            } else {
                affectedUsers = ImmutableList.of();
            }
        } else {
            // deleting the reference to TS if the user leaves
            if (wasJoined) {
                // retrieve the affected users _before_ the user's state is changed
                affectedUsers = getJoinedUserIDs();

                _f._db.setState(_sid, user.id(), newState);
                removeTeamServerForUserImpl(user);
            } else {
                _f._db.setState(_sid, user.id(), newState);
                // joined users are not affected
                affectedUsers = ImmutableList.of();
            }
        }

        return affectedUsers;
    }

    /**
     * @return null iff the user doesn't exist
     */
    public @Nullable SharedFolderState getStateNullable(User user)
            throws SQLException
    {
        return _f._db.getStateNullable(_sid, user.id());
    }

    /**
     * Mark shared folder as external root for a given user
     *
     * NB: the external flag should be set upon sharing/joining and never modified afterwards
     */
    public void setExternal(User user, boolean external)
            throws SQLException, ExNotFound, ExAlreadyExist
    {
        _f._db.setExternal(_sid, user.id(), external);
    }

    public boolean isExternal(User user) throws SQLException
    {
        return _f._db.isExternal(_sid, user.id());
    }

    /**
     * Add the user's Team Server ID to the ACL. No-op if there are other users on the shared folder
     * belonging to the same Team Server.
     */
    public ImmutableCollection<UserID> addTeamServerForUser(User user)
            throws ExNotFound, ExAlreadyExist, SQLException
    {
        if (addTeamServerForUserImpl(user)) {
            return getJoinedUserIDs();
        } else {
            return ImmutableSet.of();
        }
    }

    /**
     * No-op if the Team Server ACL already exists
     *
     * @return whether actual operations are performed
     */
    private boolean addTeamServerForUserImpl(User user)
            throws ExNotFound, SQLException, ExAlreadyExist
    {
        User tsUser = user.getOrganization().getTeamServerUser();
        if (getPermissionsNullable(tsUser) == null) {
            // Don't call this.addJoinedUser() here as it would cause recursion
            _f._db.insertUser(_sid, tsUser.id(), Permissions.EDITOR, JOINED, null);
            return true;
        } else {
            return false;
        }
    }

    public ImmutableCollection<UserID> removeUser(User user)
            throws SQLException, ExNotFound, ExNoAdminOrOwner
    {
        return removeUserAndTransferOwnership(user, null);
    }

    public ImmutableCollection<UserID> removeUserAndTransferOwnership(User user, @Nullable User newOwner)
            throws SQLException, ExNotFound, ExNoAdminOrOwner
    {
        ImmutableCollection<UserID> affected = removeUserAllowNoOwner(user);
        if (exists() && !hasOwnerLeft()) {
            if (newOwner == null) throwIfNoOwnerLeft();
            try {
                addJoinedUser(newOwner, Permissions.OWNER);
            } catch (ExAlreadyExist e) {
                // in general exception-driven control flow is bad but here we need the
                // try-catch block anyway (if only to fill it with an assertion) and the
                // likelihood of the new owner already being a member but not and OWNER
                // is very low...
                grantPermission(newOwner, Permission.MANAGE);
            }
        }
        return affected;
    }

    private ImmutableCollection<UserID> removeUserAllowNoOwner(User user)
            throws SQLException, ExNotFound
    {
        boolean isJoined = getStateNullable(user) == JOINED;

        // retrieve the list of affected users _before_ performing the deletion, so that all the
        // users including the removed ones will get notified. Don't notify any one if the user
        // was not joined.
        ImmutableCollection<UserID> affectedUsers =
                isJoined ? getJoinedUserIDs() : ImmutableList.<UserID>of();

        _f._db.delete(_sid, user.id());

        // remove the Team Server only if the user has joined the folder
        if (isJoined) removeTeamServerForUserImpl(user);

        // auto-destroy folder if empty
        if (getJoinedUserIDs().isEmpty()) {
            destroy();
        }

        return affectedUsers;
    }

    /**
     * Remove the user's Team Server from the folder. No-op if there are other users on the shared
     * folder belonging to the same Team Server.
     */
    public ImmutableCollection<UserID> removeTeamServerForUser(User user)
            throws SQLException, ExNotFound
    {
        ImmutableCollection<UserID> affectedUsers = getJoinedUserIDs();

        if (removeTeamServerForUserImpl(user)) {
            return affectedUsers;
        } else {
            return ImmutableSet.of();
        }
    }

    /**
     * @return whether actual operations are performed.
     *
     * This method is idempotent if called multiple times with the same parameter
     */
    private boolean removeTeamServerForUserImpl(User user)
            throws SQLException, ExNotFound
    {
        Organization org = user.getOrganization();

        // the TeamServer user can only be an OWNER of a shared folder if the shared folder
        // was created from a TeamServer, in which case we don't want the TeamServer ACL to be
        // affected by the coming and going of users of the organization
        if (hasPermission(org.getTeamServerUser(), Permission.MANAGE)) return false;

        for (UserID otherUser : getJoinedUserIDs()) {
            if (otherUser.equals(user.id())) continue;
            if (otherUser.isTeamServerID()) continue;
            if (_f._factUser.create(otherUser).belongsTo(org)) return false;
        }

        _f._db.delete(_sid, org.id().toTeamServerUserID());
        return true;
    }

    public boolean hasPermission(User user, Permission permission) throws SQLException
    {
        Permissions permissions = getPermissionsNullable(user);
        return permissions != null && permissions.covers(permission);
    }

    /**
     * N.B the return value include Team Servers
     */
    public ImmutableMap<User, Permissions> getJoinedUsersAndRoles()
            throws SQLException
    {
        ImmutableMap.Builder<User, Permissions> builder = ImmutableMap.builder();
        for (SubjectPermissions srp : _f._db.getJoinedUsersAndRoles(_sid)) {
            builder.put(_f._factUser.create(srp._subject), srp._permissions);
        }
        return builder.build();
    }

    public Iterable<UserPermissionsAndState> getAllUsersRolesAndStates() throws SQLException
    {
        ImmutableList.Builder<UserPermissionsAndState> builder = ImmutableList.builder();
        for (UserIDRoleAndState entry : _f._db.getAllUsersRolesAndStates(_sid)) {
            builder.add(new UserPermissionsAndState(_f._factUser.create(entry._userID),
                    entry._permissions, entry._state));
        }
        return builder.build();
    }

    public ImmutableCollection<User> getJoinedUsers()
            throws SQLException
    {
        ImmutableList.Builder<User> builder = ImmutableList.builder();
        for (UserID userID : getJoinedUserIDs()) builder.add(_f._factUser.create(userID));
        return builder.build();
    }

    public ImmutableCollection<UserID> getJoinedUserIDs()
            throws SQLException
    {
        return _f._db.getJoinedUsers(_sid);
    }

    /**
     * @return all the users including Team Servers
     */
    public ImmutableCollection<User> getAllUsers() throws SQLException
    {
        Builder<User> builder = ImmutableList.builder();
        for (UserID userID : _f._db.getAllUsers(_sid)) {
            builder.add(_f._factUser.create(userID));
        }
        return builder.build();
    }

    /**
     * @return all the users except Team Servers
     */
    public ImmutableCollection<User> getAllUsersExceptTeamServers() throws SQLException
    {
        Builder<User> builder = ImmutableList.builder();
        for (UserID userID : _f._db.getAllUsers(_sid)) {
            if (!userID.isTeamServerID()) builder.add(_f._factUser.create(userID));
        }
        return builder.build();
    }

    /**
     * Replace current permissions of an existing member with a new set of permissions.
     * @return List of affected users for which ACL epochs should be bumped
     */
    public ImmutableCollection<UserID> setPermissions(User user, Permissions permissions)
            throws ExNoAdminOrOwner, ExNotFound, SQLException
    {
        _f._db.setPermissions(_sid, user.id(), permissions);
        return usersAffectedByPermissionsChange(user);
    }

    /**
     * Grant a specific permisison to an existing user
     * @return List of affected users for which ACL epochs should be bumped
     */
    public ImmutableCollection<UserID> grantPermission(User user, Permission permission)
            throws ExNoAdminOrOwner, ExNotFound, SQLException
    {
        _f._db.grantPermission(_sid, user.id(), permission);
        return usersAffectedByPermissionsChange(user);
    }

    /**
     * Revoke a specific permisison for an existing user
     * @return List of affected users for which ACL epochs should be bumped
     */
    public ImmutableCollection<UserID> revokePermission(User user, Permission permission)
            throws ExNoAdminOrOwner, ExNotFound, SQLException
    {
        _f._db.revokePermission(_sid, user.id(), permission);
        return usersAffectedByPermissionsChange(user);
    }

    private ImmutableCollection<UserID> usersAffectedByPermissionsChange(User user)
            throws ExNoAdminOrOwner, SQLException
    {
        throwIfNoOwnerLeft();
        return getStateNullable(user) == JOINED ? getJoinedUserIDs() : ImmutableList.<UserID>of();
    }

    /**
     * @return the role of the given user. Return null iff. the user doesn't exist
     */
    public @Nullable Permissions getPermissionsNullable(User user)
            throws SQLException
    {
        return _f._db.getRoleNullable(_sid, user.id());
    }

    public boolean hasOwnerLeft() throws SQLException
    {
        return _f._db.hasOwner(_sid);
    }

    private void throwIfNoOwnerLeft()
            throws ExNoAdminOrOwner, SQLException
    {
        if (!hasOwnerLeft()) {
            throw new ExNoAdminOrOwner("There must be at least one owner per shared folder");
        }
    }

    /**
     * A user has privileges to change ACLs if and only if:
     *  1. the user is the owner of the folder, or
     *  2. the user is the organization admin of at least one non-pending owner of the folder.
     */
    public void throwIfNoPrivilegeToChangeACL(User user)
            throws SQLException, ExNoPerm, ExNotFound
    {
        // Bypass the following expensive tests for common cases.
        if (isJoinedOwner(user)) return;

        // See if the user is the organization admin of a non-pending owner of the folder.
        // NB: TeamServer user is treated as an org admin as it can only be setup by an org admin...
        if (user.isAdmin() || user.id().isTeamServerID()) {
            Organization org = user.getOrganization();
            for (SubjectPermissions srp : _f._db.getJoinedUsersAndRoles(_sid)) {
                if (srp._permissions.covers(Permission.MANAGE)) {
                    User member = _f._factUser.create(srp._subject);
                    if (member.belongsTo(org)) return;
                }
            }
        }

        throw new ExNoPerm("Not allowed to manage users for this shared folder");
    }

    private boolean isJoinedOwner(User user)
            throws SQLException
    {
        Permissions permissions = getPermissionsNullable(user);
        return permissions != null && permissions.covers(Permission.MANAGE) && getStateNullable(user) == JOINED;
    }

    /**
     * @return the sharer (i.e. inviter) of the given user. Return null if the user is not invited
     * by anyone (e.g. the initial owner, Team Server users).
     */
    public @Nullable User getSharerNullable(User user)
            throws SQLException
    {
        UserID sharer = _f._db.getSharerNullable(_sid, user.id());
        return sharer == null ? null : _f._factUser.create(sharer);
    }

    // the only purpose of this class is to hold the result of getAllUsersRolesAndStates()
    public static class UserPermissionsAndState
    {
        @Nonnull public final User _user;
        @Nonnull public final Permissions _permissions;
        @Nonnull public final SharedFolderState _state;

        public UserPermissionsAndState(User user, Permissions permissions, SharedFolderState state)
        {
            _user = user;
            _permissions = permissions;
            _state = state;
        }

        @Override
        public boolean equals(Object that)
        {
            return this == that ||
                    (that instanceof UserPermissionsAndState &&
                            _user.equals(((UserPermissionsAndState)that)._user) &&
                            _permissions.equals(((UserPermissionsAndState)that)._permissions) &&
                            _state.equals(((UserPermissionsAndState)that)._state));
        }

        @Override
        public int hashCode()
        {
            return _user.hashCode() ^ _permissions.hashCode() ^ _state.hashCode();
        }
    }
}
