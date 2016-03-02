/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.sf;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.GroupID;
import com.aerofs.ids.SID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.rest.auth.IUserAuthToken;
import com.aerofs.rest.auth.OAuthRequestFilter;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.base.ParamFactory;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.group.GroupSharesDatabase;
import com.aerofs.sp.server.lib.group.GroupSharesDatabase.GroupIDAndRole;
import com.aerofs.sp.server.lib.sf.SharedFolderDatabase.UserIDRoleAndState;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import com.sun.jersey.api.core.HttpContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.Map.Entry;

import static com.aerofs.sp.common.SharedFolderState.JOINED;
import static com.aerofs.sp.common.SharedFolderState.LEFT;
import static com.aerofs.sp.common.SharedFolderState.PENDING;
import static com.google.common.base.Preconditions.checkArgument;

public class SharedFolder
{
    public static class Factory
    {
        private SharedFolderDatabase _db;
        private GroupSharesDatabase _gsdb;
        private Group.Factory _factGroup;
        private User.Factory _factUser;

        @Inject
        public void inject(SharedFolderDatabase db, GroupSharesDatabase gsdb,
                Group.Factory factGroup, User.Factory factUser)
        {
            _db = db;
            _gsdb = gsdb;
            _factGroup = factGroup;
            _factUser = factUser;
        }

        public SharedFolder create(ByteString sid)
        {
            return create(new SID(BaseUtil.fromPB(sid)));
        }

        public SharedFolder create(SID sid)
        {
            return new SharedFolder(this, sid);
        }

        public SharedFolder create(String s)
        {
            try {
                return create(s.contains("@") ? SID.rootSID(UserID.fromExternal(s)) : new SID(s));
            } catch (ExInvalidID e) {
                throw new IllegalArgumentException("Invalid SID");
            }
        }

        @ParamFactory
        public SharedFolder create(String s, HttpContext cxt)
        {
            if (s.equals("root")) {
                IUserAuthToken token = (IUserAuthToken)cxt.getProperties().get(OAuthRequestFilter.OAUTH_TOKEN);
                s = token.user().getString();
            }
            return create(s);
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
    public String getName(@Nullable User user)
            throws ExNotFound, SQLException
    {
        return _f._db.getName(_sid, user == null ? null : user.id());
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
     * @return A map of user IDs to epochs to be published via lipwig.
     * @throws com.aerofs.base.ex.ExAlreadyExist if the store already exists
     * @throws com.aerofs.base.ex.ExNotFound if the owner is not found
     */
    public ImmutableCollection<UserID> save(String folderName, User owner)
            throws ExNotFound, SQLException, ExAlreadyExist, ExNoPerm
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
     * @return A set of user IDs for which epoch should be increased and published via lipwig
     */
    public ImmutableCollection<UserID> destroy()
            throws SQLException
    {
        ImmutableCollection<UserID> joined = getJoinedUserIDs();
        for (GroupID gid : getJoinedGroupIDs()) {
            _f._gsdb.removeSharedFolder(gid, _sid);
        }
        _f._db.destroy(_sid);
        return joined;
    }

    /**
     * @return A set of user IDs for which epoch should be increased and published via lipwig
     * @throws com.aerofs.base.ex.ExAlreadyExist if the user is already added.
     */
    public ImmutableCollection<UserID> addJoinedUser(User user, Permissions permissions)
            throws ExAlreadyExist, SQLException, ExNotFound, ExNoPerm
    {
        insertUser(_sid, user.id(), permissions, JOINED, null, GroupID.NULL_GROUP);
        setState(user, JOINED);

        addTeamServerForUserImpl(user);

        return getJoinedUserIDs();
    }

    public void addPendingUser(User user, Permissions permissions, User sharer)
            throws SQLException, ExAlreadyExist, ExNoPerm
    {
        if (getStateNullable(user) != null) {
            throw new ExAlreadyExist("(userID, SID) pair already exist in the ACL table");
        }
        insertUser(_sid, user.id(), permissions, PENDING, sharer.id(), GroupID.NULL_GROUP);
    }

    /** Adds an ACL specific to a group, inherits the state of any already existing ACL for this
     * shared folder and user - if this is the first time the user is joining the folder, sets the
     * state as PENDING
     * @return whether or not we need to send an email inviting the user to the folder
     */
    public AffectedAndNeedsEmail addUserWithGroup(User user, @Nullable Group group,
            Permissions permissions, @Nullable User sharer)
            throws SQLException, ExAlreadyExist, ExNotFound, ExNoPerm
    {
        GroupID gid = group == null ? GroupID.NULL_GROUP : group.id();
        UserID sharerID = sharer == null ? null : sharer.id();

        // N.B. This check is only relevent when group is null i.e. user inviting another user.
        if (gid == GroupID.NULL_GROUP && (!user.exists() && sharer != null &&
                !sharer.canInviteNewUsers()))
        {
            throw new ExNoPerm(user.id() + " is currently not invited to AeroFS." +
                    " Please contact your AeroFS administrator to invite the user.");
        }

        SharedFolderState oldState = getStateNullable(user), newState = oldState;
        Permissions oldPermissions = getPermissionsNullable(user);
        boolean needsEmail = false;
        if (oldState == LEFT) {
            _f._db.setState(id(), user.id(), PENDING);
        }
        if (oldState == null || oldState == LEFT) {
            newState = PENDING;
            needsEmail = true;
        }

        // insert a new ACL if the row doesn't exist (first half of the if condition), or if the
        // new state isn't pending - so we can throw ExAlreadyExist on already joined users
        if (getPermissionsInGroupNullable(user, group) == null || newState != PENDING) {
            insertUser(_sid, user.id(), permissions, newState, sharerID, gid);
        }

        ImmutableCollection<UserID> affected = needToUpdateACLs(oldState, oldPermissions, newState,
                getPermissionsNullable(user)) ? getJoinedUserIDs() : ImmutableList.of();
        return new AffectedAndNeedsEmail(affected, needsEmail);
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
     * @return true iff the shared folder's membership is locked
     */
    public boolean isLocked() throws SQLException
    {
        return _f._db.isLocked(_sid);
    }

    public void setLocked() throws SQLException, ExNotFound
    {
        _f._db.setLocked(_sid);
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
            _f._db.insertUser(_sid, tsUser.id(), Permissions.EDITOR, JOINED, null, GroupID.NULL_GROUP);
            return true;
        } else {
            return false;
        }
    }

    public ImmutableCollection<UserID> removeIndividualUser(User user)
            throws ExNoAdminOrOwner, SQLException, ExNotFound, ExNoPerm
    {
        return removeUserAndTransferOwnership(user, null, null);
    }

    public ImmutableCollection<UserID> removeUserWithGroup(User user, Group group)
            throws SQLException, ExNotFound, ExNoAdminOrOwner, ExNoPerm
    {
        return removeUserAndTransferOwnership(user, null, group);
    }

    public ImmutableCollection<UserID> removeGroup(Group group)
            throws SQLException, ExNotFound, ExNoAdminOrOwner
    {
        ImmutableCollection<UserID> affected = getJoinedUserIDs();
        ImmutableMap<User, Permissions> prev = getJoinedUsersAndRoles();
        for (User u : group.listMembers()) {
            // N.B. this shouldn't throw ExNotFound because the group's users can't remove
            // themselves from a shared folder, they can only remove the group
            _f._db.delete(id(), u.id(), group.id());
        }
        ImmutableMap<User, Permissions> curr = getJoinedUsersAndRoles();

        // difference of prev and curr keySets is users that used to be joined and no longer are
        for (User removed : Sets.difference(prev.keySet(), curr.keySet())) {
            // we may have already removed the team server due to other removed user
            if (getPermissionsNullable(removed.getOrganization().getTeamServerUser()) == null) {
                continue;
            }

            removeTeamServerForUserImpl(removed);
        }

        if (getJoinedUserIDs().isEmpty()) {
            destroy();
        } else {
            throwIfNoOwnerLeft();
        }

        return needToUpdateACLs(prev, curr) ? affected : ImmutableList.of();
    }

    public ImmutableCollection<UserID> removeUserAndTransferOwnership(User user,
            @Nullable User newOwner, @Nullable Group group)
            throws SQLException, ExNotFound, ExNoAdminOrOwner, ExNoPerm
    {
        ImmutableCollection<UserID> affected = removeUserAllowNoOwner(user, group);
        if (exists() && !hasOwnerLeft()) {
            if (newOwner == null) throwIfNoOwnerLeft();
            try {
                addJoinedUser(newOwner, Permissions.OWNER);
            } catch (ExAlreadyExist e) {
                // in general exception-driven control flow is bad but here we need the
                // try-catch block anyway (if only to fill it with an assertion) and the
                // likelihood of the new owner already being a member but not an OWNER
                // is very low...
                grantPermission(newOwner, Permission.MANAGE);
            }
        }
        return affected;
    }

    private ImmutableCollection<UserID> removeUserAllowNoOwner(User user, @Nullable Group group)
            throws SQLException, ExNotFound
    {
        SharedFolderState oldState = getStateNullable(user);
        Permissions oldPermissions = getPermissionsNullable(user);
        boolean wasJoined = oldState == JOINED;
        GroupID gid = group == null ? GroupID.NULL_GROUP : group.id();

        // retrieve the list of affected users _before_ performing the deletion, so that all the
        // users including the removed ones will get notified. Don't notify any one if the user
        // was not joined.
        ImmutableCollection<UserID> affectedUsers =
                wasJoined ? getJoinedUserIDs() : ImmutableList.<UserID>of();

        _f._db.delete(_sid, user.id(), gid);

        SharedFolderState newState = getStateNullable(user);
        Permissions newPermissions = getPermissionsNullable(user);
        boolean nowJoined = newState == JOINED;
        // remove the Team Server only if the user has joined the folder
        if (wasJoined && !nowJoined) removeTeamServerForUserImpl(user);

        // auto-destroy folder if empty
        if (getJoinedUserIDs().isEmpty()) destroy();

        return needToUpdateACLs(oldState, oldPermissions, newState, newPermissions) ?
                affectedUsers : ImmutableList.of();
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
     * @throws ExNotFound if the team server user for the user is not a member
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

        _f._db.delete(_sid, org.id().toTeamServerUserID(), GroupID.NULL_GROUP);
        return true;
    }

    public boolean hasPermission(User user, Permission permission) throws SQLException
    {
        Permissions permissions = getPermissionsNullable(user);
        return permissions != null && permissions.covers(permission);
    }

    /**
     * N.B the return value include Team Servers
     *
     * this method is used to propagate ACLs, so replies already incorporate Groups
     */
    public ImmutableMap<User, Permissions> getJoinedUsersAndRoles()
            throws SQLException
    {
        ImmutableMap.Builder<User, Permissions> builder = ImmutableMap.builder();
        for (Entry<UserID, Permissions> up : _f._db.getJoinedUsersAndRoles(_sid).entrySet()) {
            builder.put(_f._factUser.create(up.getKey()), up.getValue());
        }
        return builder.build();
    }

    public Iterable<UserPermissionsAndState> getAllUsersRolesAndStates()
            throws SQLException
    {
        ImmutableList.Builder<UserPermissionsAndState> builder = ImmutableList.builder();
        for (UserIDRoleAndState entry : _f._db.getAllUsersRolesAndStates(_sid)) {
            builder.add(new UserPermissionsAndState(_f._factUser.create(entry._userID),
                    entry._permissions, entry._state));
        }
        return builder.build();
    }

    /**
     * Used to get the User roles for displaying to users, ignoring other groups
     */
    public Iterable<UserPermissionsAndState> getUserRolesAndStatesForGroup(@Nullable Group group)
            throws SQLException
    {
        GroupID gid = group == null ? GroupID.NULL_GROUP : group.id();

        ImmutableList.Builder<UserPermissionsAndState> builder = ImmutableList.builder();
        for (UserIDRoleAndState entry : _f._db.getUserRolesAndStatesWithGroup(_sid, gid)) {
            builder.add(new UserPermissionsAndState(_f._factUser.create(entry._userID),
                    entry._permissions, entry._state));
        }
        return builder.build();
    }

    public Iterable<GroupPermissions> getAllGroupsAndRoles() throws SQLException
    {
        ImmutableList.Builder<GroupPermissions> builder = ImmutableList.builder();
        for (GroupIDAndRole groupIDAndRole : _f._gsdb.listJoinedGroupsAndRoles(id())) {
            builder.add(new GroupPermissions(_f._factGroup.create(groupIDAndRole._groupID),
                    groupIDAndRole._permissions));
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

    public ImmutableCollection<Group> getJoinedGroups()
            throws SQLException
    {
        Builder<Group> builder = ImmutableList.builder();
        for (GroupID gid : getJoinedGroupIDs()) {
            builder.add(_f._factGroup.create(gid));
        }
        return builder.build();
    }

    public ImmutableCollection<UserID> getJoinedUserIDs()
            throws SQLException
    {
        return _f._db.getJoinedUsers(_sid);
    }

    public ImmutableCollection<GroupID> getJoinedGroupIDs()
            throws SQLException
    {
        Builder<GroupID> builder = ImmutableList.builder();
        builder.addAll(_f._gsdb.listJoinedGroups(id()));
        return builder.build();
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
        Permissions prevRole = getPermissionsNullable(user);
        _f._db.setPermissions(_sid, user.id(), permissions);
        throwIfNoOwnerLeft();
        SharedFolderState state = getStateNullable(user);
        Permissions currRole = getPermissionsNullable(user);
        return needToUpdateACLs(state, prevRole, state, currRole) ?
                getJoinedUserIDs() : ImmutableList.of();

    }

    public ImmutableCollection<UserID> setPermissionsForGroup(Group group, Permissions permissions)
            throws ExNoAdminOrOwner, ExNotFound, SQLException
    {
        ImmutableMap<User, Permissions> prev = getJoinedUsersAndRoles();
        _f._db.setPermissionsForGroup(id(), group.id(), permissions);
        throwIfNoOwnerLeft();
        ImmutableMap<User, Permissions> curr = getJoinedUsersAndRoles();
        return needToUpdateACLs(prev, curr) ? getJoinedUserIDs() : ImmutableList.of();
    }

    /**
     * Grant a specific permission to an existing user
     * @return List of affected users for which ACL epochs should be bumped
     */
    public ImmutableCollection<UserID> grantPermission(User user, Permission permission)
            throws ExNoAdminOrOwner, ExNotFound, SQLException
    {
        Permissions prevRole = getPermissionsNullable(user);
        _f._db.grantPermission(_sid, user.id(), permission);
        throwIfNoOwnerLeft();
        SharedFolderState state = getStateNullable(user);
        Permissions currRole = getPermissionsNullable(user);
        return needToUpdateACLs(state, prevRole, state, currRole) ?
                getJoinedUserIDs() : ImmutableList.of();
    }

    /**
     * Revoke a specific permission for an existing user across all their ACLs, including groups
     * this method is used to make sure a user does not have a specific permission, do not use
     * it for the normal modification of a user's role in a folder
     * @return List of affected users for which ACL epochs should be bumped
     */
    public ImmutableCollection<UserID> revokePermission(User user, Permission permission)
            throws ExNoAdminOrOwner, ExNotFound, SQLException
    {
        Permissions prevRole = getPermissionsNullable(user);
        _f._db.revokePermission(_sid, user.id(), permission);
        throwIfNoOwnerLeft();
        SharedFolderState state = getStateNullable(user);
        Permissions currRole = getPermissionsNullable(user);
        return needToUpdateACLs(state, prevRole, state, currRole) ?
                getJoinedUserIDs() : ImmutableList.of();
    }

    /**
     * @return the role of the given user. Return null iff. the user doesn't exist
     */
    public @Nullable Permissions getPermissionsNullable(User user)
            throws SQLException
    {
        return _f._db.getEffectiveRoleNullable(_sid, user.id());
    }

    public @Nullable Permissions getPermissionsInGroupNullable(User user, @Nullable Group group)
            throws SQLException
    {
        return _f._db.getRoleNullable(_sid, user.id(),
                group == null ? GroupID.NULL_GROUP : group.id());
    }

    public Permissions getPermissions(User user)
            throws SQLException, ExNotFound
    {
        Permissions ret = getPermissionsNullable(user);
        if (ret == null) {
            throw new ExNotFound("specified user not found in this shared folder");
        }
        return ret;
    }

    public Permissions getPermissionsInGroup(User user, @Nullable Group group)
        throws SQLException, ExNotFound
    {
        Permissions ret = getPermissionsInGroupNullable(user, group);
        if (ret == null) {
            throw new ExNotFound("specified user and group not found in this shared folder");
        }
        return ret;
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
     *  2. the user is the organization admin of at least one owner of the folder (regardless of
     *     their state)
     *
     * NB: 2 is not immediately obvious from the method name and may not always be desirable
     * We should audit all callers and determine whether offering two variants (one without 2)
     * would make sense.
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
            // TODO: move that logic down into DB query
            for (UserIDRoleAndState srp : _f._db.getAllUsersRolesAndStates(_sid)) {
                if (srp._permissions.covers(Permission.MANAGE)) {
                    User member = _f._factUser.create(srp._userID);
                    if (member.exists() && member.belongsTo(org)) return;
                }
            }
        }

        throw new ExNoPerm("Not allowed to manage members of this shared folder");
    }

    private boolean isJoinedOwner(User user)
            throws SQLException
    {
        Permissions permissions = getPermissionsNullable(user);

        return permissions != null &&
               permissions.covers(Permission.MANAGE) &&
               getStateNullable(user) == JOINED;
    }

    public void throwIfNotJoinedOwner(User user)
            throws SQLException, ExNoPerm
    {
        if (isJoinedOwner(user)) return;
        throw new ExNoPerm();
    }

    // N.B. the permissions may only be null if the corresponding state is also null, we also
    // check the states for nullity before checking permission equality to avoid a NPE
    public boolean needToUpdateACLs(@Nullable SharedFolderState prevState,
            @Nullable Permissions prevPermissions, @Nullable SharedFolderState currState,
            @Nullable Permissions currPermissions)
    {
        if (prevState != JOINED && currState != JOINED) {
            // unjoined users don't have effects on ACLs
            return false;
        } else {
            // if a user joins or leaves, need to update ACLs - otherwise check permission equality
            return (prevState == JOINED) != (currState == JOINED) ||
                    !prevPermissions.equals(currPermissions);
        }
    }

    public boolean needToUpdateACLs(ImmutableMap<User, Permissions> previous,
            ImmutableMap<User, Permissions> after)
    {
        if (!previous.keySet().equals(after.keySet())) {
            return true;
        }
        for (User u : previous.keySet()) {
            if (!previous.get(u).equals(after.get(u))) {
                return true;
            }
        }
        return false;
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

    // the number of pending and active users in this folder
    public int getNumberOfActiveMembers()
            throws SQLException
    {
        int count = 0;
        for (UserPermissionsAndState ups : getAllUsersRolesAndStates()) {
            if ((ups._state == PENDING || ups._state == JOINED) && !ups._user.id().isTeamServerID())
                count++;
        }
        return count;
    }

    /**
     * This method wraps the database function but throws ExNoPerm if the folder is locked
     */
    private void insertUser(SID sid, UserID uid, Permissions permissions, SharedFolderState state,
                            @Nullable UserID sharer, GroupID gid)
            throws SQLException, ExNoPerm, ExAlreadyExist
    {
        if (this.isLocked()) {
            throw new ExNoPerm("Cannot add member to locked shared folder");
        }
        _f._db.insertUser(sid, uid, permissions, state, sharer, gid);
    }

    // the only purpose of this class is to hold the result of getAllUsersRolesAndStates()
    public static class UserPermissionsAndState
    {
        public final User _user;
        public final Permissions _permissions;
        public final SharedFolderState _state;

        public UserPermissionsAndState(@Nonnull User user, @Nonnull Permissions permissions,
                @Nonnull SharedFolderState state)
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

    public static class GroupPermissions
    {
        public final Group _group;
        public final Permissions _permissions;

        public GroupPermissions(@Nonnull Group group, @Nonnull Permissions permissions)
        {
            _group = group;
            _permissions = permissions;
        }

        @Override
        public boolean equals(Object that)
        {
            return this == that ||
                    (that instanceof GroupPermissions &&
                        _group.equals(((GroupPermissions)that)._group) &&
                        _permissions.equals(((GroupPermissions)that)._permissions));
        }

        @Override
        public int hashCode()
        {
            return _group.hashCode() ^ _permissions.hashCode();
        }
    }

    public static class AffectedAndNeedsEmail
    {
        public final ImmutableCollection<UserID> _affected;
        public final Boolean _needsEmail;

        public AffectedAndNeedsEmail(@Nonnull ImmutableCollection<UserID> affected,
                @Nonnull Boolean needsEmail)
        {
            _affected = affected;
            _needsEmail = needsEmail;
        }

        @Override
        public boolean equals(Object that)
        {
            return this == that ||
                    (that instanceof AffectedAndNeedsEmail &&
                            _affected.equals(((AffectedAndNeedsEmail)that)._affected) &&
                            _needsEmail.equals(((AffectedAndNeedsEmail)that)._needsEmail));
        }

        @Override
        public int hashCode()
        {
            return _affected.hashCode() ^ _needsEmail.hashCode();
        }
    }
}
