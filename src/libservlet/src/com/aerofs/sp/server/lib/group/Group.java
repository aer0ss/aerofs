/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.group;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExNotLocallyManaged;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.server.lib.sf.SharedFolder.AffectedAndNeedsEmail;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.Set;

public class Group
{
    private final static Logger l = Loggers.getLogger(Group.class);

    public static class Factory
    {
        private GroupDatabase _gdb;
        private GroupMembersDatabase _gmdb;
        private GroupSharesDatabase _gsdb;

        private Organization.Factory _factOrg;
        private SharedFolder.Factory _factSharedFolder;
        private User.Factory _factUser;

        @Inject
        public void inject(GroupDatabase gdb, GroupMembersDatabase gmdb, GroupSharesDatabase gsdb,
                Organization.Factory factOrg, SharedFolder.Factory factSharedFolder,
                User.Factory factUser)
        {
            _gdb = gdb;
            _gmdb = gmdb;
            _gsdb = gsdb;
            _factOrg = factOrg;
            _factSharedFolder = factSharedFolder;
            _factUser = factUser;
        }

        public Group create(int gid)
                throws ExBadArgs
        {
            return new Group(this, GroupID.fromExternal(gid));
        }

        public Group create(GroupID gid)
        {
            return new Group(this, gid);
        }

        /**
         * Add a new group and generate a new group ID.
         */
        public Group save(String commonName, OrganizationID orgId, @Nullable byte[] externalId)
                throws SQLException
        {
            while (true) {
                // Use a random ID only to prevent competitors from figuring out total number of
                // groups. It is NOT a security measure.
                int newID = Util.rand().nextInt();
                try {
                    GroupID gid = GroupID.fromExternal(newID);
                    _gdb.createGroup(gid, commonName, orgId, externalId);
                    return create(gid);
                } catch (ExAlreadyExist e) {
                    l.info("duplicate group id " + newID + ". trying a new one.");
                } catch (ExBadArgs e) {
                    l.info("hit a reserved group id of " + newID);
                }
            }
        }

        public Group save(GroupID gid, String commonName, OrganizationID orgId,
                @Nullable byte[] externalId)
                throws SQLException, ExAlreadyExist
        {
            _gdb.createGroup(gid, commonName, orgId, externalId);
            return create(gid);
        }
    }

    private final GroupID _gid;
    private final Factory _f;

    private Group(Factory f, GroupID gid)
    {
        _f = f;
        _gid = gid;
    }

    @Override
    public int hashCode()
    {
        return _gid.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && _gid.equals(((Group)o)._gid));
    }

    @Override
    public String toString()
    {
        return "group " + _gid;
    }

    public GroupID id()
    {
        return _gid;
    }

    public boolean exists()
            throws SQLException
    {
        return _f._gdb.hasGroup(_gid);
    }

    public void throwIfDoesNotExist()
            throws SQLException, ExNotFound
    {
        if (!exists()) {
            throw new ExNotFound();
        }
    }

    public Organization getOrganization()
            throws SQLException, ExNotFound
    {
        return _f._factOrg.create(_f._gdb.getOrganizationID(_gid));
    }

    public String getCommonName()
            throws SQLException, ExNotFound
    {
        return _f._gdb.getCommonName(_gid);
    }

    public void setCommonName(String commonName)
            throws SQLException, ExNotFound
    {
        throwIfDoesNotExist();
        _f._gdb.setCommonName(id(), commonName);
    }

    public
    @Nullable
    byte[] getExternalIdNullable()
            throws SQLException, ExNotFound
    {
        return _f._gdb.getExternalIdNullable(id());
    }

    public boolean isExternallyManaged()
            throws SQLException, ExNotFound
    {
        return _f._gdb.getExternalIdNullable(id()) != null;
    }

    public boolean isLocallyManaged()
            throws SQLException, ExNotFound
    {
        return _f._gdb.getExternalIdNullable(id()) == null;
    }

    public void throwIfExternallyManaged()
            throws SQLException, ExNotFound, ExNotLocallyManaged
    {
        if (isExternallyManaged()) {
            throw new ExNotLocallyManaged();
        }
    }

    public ImmutableCollection<UserID> delete()
            throws SQLException, ExNotFound, ExNoAdminOrOwner
    {
        throwIfDoesNotExist();

        ImmutableSet.Builder<UserID> affected = ImmutableSet.builder();
        for (User u : listMembers()) {
            affected.addAll(removeMember(u, null));
        }

        // Order matters, or else we will die on foreign key constraints.
        _f._gsdb.deleteSharesFor(id());
        _f._gmdb.deleteMembersFor(id());
        _f._gdb.deleteGroup(id());

        return affected.build();
    }

    // ---
    // Membership
    // ---

    /**
     * @return a set of UserIDs that need to update their ACLs and a list of SIDs that a user needs
     * an email invitation to
     */
    public AffectedUserIDsAndInvitedFolders addMember(User user)
            throws
            SQLException,
            ExAlreadyExist,
            ExNotFound
    {
        // Group members db does not know about group existence, so we must check that separately.
        throwIfDoesNotExist();

        // Constraint violation triggers the ExAlreadyExist exception; no need to handle explicitly
        _f._gmdb.addMember(id(), user.id());

        Set<SharedFolder> invitedTo = Sets.newHashSet();
        ImmutableCollection.Builder<UserID> needsACLUpdate = ImmutableSet.builder();
        for (SharedFolder sf : listSharedFolders()) {
            Permissions p = _f._gsdb.getRole(id(), sf.id());
            AffectedAndNeedsEmail updates = sf.addUserWithGroup(user, this, p, null);
            needsACLUpdate.addAll(updates._affected);
            if (updates._needsEmail) {
                invitedTo.add(sf);
            }
        }
        return new AffectedUserIDsAndInvitedFolders(needsACLUpdate.build(), invitedTo);
    }

    public ImmutableCollection<UserID> removeMember(User user, @Nullable User newOwner)
            throws SQLException, ExNotFound, ExNoAdminOrOwner
    {
        // don't need to check if group exists, hasMember will do that
        if (!hasMember(user)) {
            throw new ExNotFound(user + " not in " + this);
        }

        _f._gmdb.removeMember(id(), user.id());

        ImmutableSet.Builder<UserID> affected = ImmutableSet.builder();
        for (SharedFolder sf : listSharedFolders()) {
            affected.addAll(sf.removeUserAndTransferOwnership(user, newOwner, this));
        }
        return affected.build();
    }

    public boolean hasMember(User user)
            throws SQLException, ExNotFound
    {
        throwIfDoesNotExist();
        return _f._gmdb.hasMember(id(), user.id());
    }

    public ImmutableList<User> listMembers()
            throws SQLException, ExNotFound
    {
        throwIfDoesNotExist();
        return userFromEmails(_f._gmdb.listMembers(id()));
    }

    private ImmutableList<User> userFromEmails(Iterable<UserID> emails)
            throws SQLException
    {
        ImmutableList.Builder<User> builder = ImmutableList.builder();
        for (UserID userID : emails) {
            builder.add(_f._factUser.create(userID));
        }
        return builder.build();
    }
    // ---
    // Shares
    // ---

    /**
     * @return a collection of UserIDs that need updating and a list of users that need to receive
     * email invitations
     */
    public AffectedUserIDsAndInvitedUsers joinSharedFolder(SharedFolder sf, Permissions permissions, User sharer)
            throws SQLException, ExAlreadyExist, ExNotFound, ExNoAdminOrOwner
    {
        throwIfDoesNotExist();
        _f._gsdb.addSharedFolder(id(), sf.id(), permissions);

        ImmutableCollection.Builder<UserID>affected = ImmutableSet.builder();
        Set<User> needEmails = Sets.newHashSet();
        for (User sharee : listMembers()) {
            //TODO (RD) addUserWithGroup makes unnecessary DB calls here to find if the permissions changed, might cause performance issues
            //really only need two large queries at the beginning and end, instead of on each user add
            AffectedAndNeedsEmail updates = sf.addUserWithGroup(sharee, this, permissions, sharer);
            affected.addAll(updates._affected);
            if (updates._needsEmail) {
                needEmails.add(sharee);
            }
        }

        return new AffectedUserIDsAndInvitedUsers(affected.build(), needEmails);
    }

    public ImmutableCollection<UserID> deleteSharedFolder(SharedFolder sf)
            throws SQLException, ExNotFound, ExNoAdminOrOwner
    {
        if (!inSharedFolder(sf)) {
            throw new ExNotFound(sf + " not in " + this);
        }
        _f._gsdb.removeSharedFolder(id(), sf.id());

        return sf.removeGroup(this);
    }

    public ImmutableCollection<UserID> changeRoleInSharedFolder(SharedFolder sf, Permissions newRole)
            throws SQLException, ExNotFound, ExNoAdminOrOwner
    {
        if (!inSharedFolder(sf)) {
            throw new ExNotFound(sf + " not in " + this);
        }
        _f._gsdb.setRoleForSharedFolder(id(), sf.id(), newRole);

        return sf.setPermissionsForGroup(this, newRole);
    }

    public boolean inSharedFolder(SharedFolder sf)
            throws SQLException, ExNotFound
    {
        throwIfDoesNotExist();
        return _f._gsdb.inSharedFolder(id(), sf.id());
    }

    public ImmutableCollection<SharedFolder> listSharedFolders()
            throws SQLException, ExNotFound
    {
        throwIfDoesNotExist();

        ImmutableList.Builder<SharedFolder> builder = ImmutableList.builder();
        for (SID sid : _f._gsdb.listSharedFolders(id())) {
            builder.add(_f._factSharedFolder.create(sid));
        }
        return builder.build();
    }

    public Permissions getRoleForSharedFolder(SharedFolder sf)
            throws SQLException, ExNotFound
    {
        return _f._gsdb.getRole(id(), sf.id());
    }

    public static class AffectedUserIDsAndInvitedUsers
    {
        public final ImmutableCollection<UserID> _affected;
        public final Set<User> _users;

        public AffectedUserIDsAndInvitedUsers(@Nonnull ImmutableCollection<UserID> affected,
                @Nonnull Set<User> users)
        {
            _affected = affected;
            _users = users;
        }

        @Override
        public boolean equals(Object that)
        {
            return this == that ||
                    (that instanceof AffectedUserIDsAndInvitedUsers &&
                            _affected.equals(((AffectedUserIDsAndInvitedUsers)that)._affected) &&
                            _users.equals(((AffectedUserIDsAndInvitedUsers)that)._users));
        }

        @Override
        public int hashCode()
        {
            return _affected.hashCode() ^ _users.hashCode();
        }
    }

    public static class AffectedUserIDsAndInvitedFolders
    {
        public final ImmutableCollection<UserID> _affected;
        public final Set<SharedFolder> _folders;

        public AffectedUserIDsAndInvitedFolders(@Nonnull ImmutableCollection<UserID> affected,
                @Nonnull Set<SharedFolder> folders)
        {
            _affected = affected;
            _folders = folders;
        }

        @Override
        public boolean equals(Object that)
        {
            return this == that ||
                    (that instanceof AffectedUserIDsAndInvitedFolders &&
                            _affected.equals(((AffectedUserIDsAndInvitedFolders)that)._affected) &&
                            _folders.equals(((AffectedUserIDsAndInvitedFolders)that)._folders));
        }

        @Override
        public int hashCode()
        {
            return _affected.hashCode() ^ _folders.hashCode();
        }
    }
}
