/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    public String getName()
            throws ExNoPerm, ExNotFound, SQLException
    {
        return _f._db.getName(_sid);
    }

    /**
     * Add the shared folder to db. Also add {@code owner} as the first owner.
     * @return A map of user IDs to epochs to be published via verkehr.
     * @throws ExAlreadyExist if the store already exists
     * @throws ExNotFound if the owner is not found
     */
    public ImmutableCollection<UserID> save(String folderName, User owner)
            throws ExNotFound, SQLException, ExAlreadyExist
    {
        _f._db.insert(_sid, folderName);

        try {
            return addMemberACL(owner, Role.OWNER);
        } catch (ExAlreadyExist e) {
            throw SystemUtil.fatalWithReturn(e);
        }
    }

    public void delete()
            throws SQLException
    {
        _f._db.delete(_sid);
    }

    /**
     * @return A set of user IDs for which epoch should be increased and published via verkehr
     * @throws ExAlreadyExist if the user is already added.
     */
    public ImmutableCollection<UserID> addMemberACL(User user, Role role)
            throws ExAlreadyExist, SQLException, ExNotFound
    {
        if (isMemberOrPending(user)) throw new ExAlreadyExist(user + " is already invited");

        _f._db.insertMemberACL(_sid, user.id(),
                Collections.singletonList(new SubjectRolePair(user.id(), role)));

        addTeamServerACLImpl(user);

        return _f._db.getMembers(_sid);
    }

    public void addPendingACL(User sharer, User sharee, Role role)
            throws SQLException, ExAlreadyExist
    {
        if (isMemberOrPending(sharee)) throw new ExAlreadyExist(sharee + " is already invited");

        _f._db.insertPendingACL(_sid, sharer.id(),
                Collections.singletonList(new SubjectRolePair(sharee.id(), role)));
    }

    /**
     * Set a user as pending
     */
    public ImmutableCollection<UserID> setPending(User user) throws SQLException, ExNotFound
    {
        ImmutableCollection<UserID> affectedUsers = _f._db.getMembers(_sid);

        _f._db.setPending(_sid, user.id(), true);

        // in terms of ACL, marking a user as pending is the same as kicking him out of the folder
        // so we need to update team server ACLs to reflect that change
        deleteTeamServerACLImpl(user);

        return affectedUsers;
    }

    /**
     * Set a user as member
     */
    public ImmutableCollection<UserID> setMember(User user)
            throws SQLException, ExNotFound, ExAlreadyExist
    {
        _f._db.setPending(_sid, user.id(), false);

        addTeamServerACLImpl(user);

        return _f._db.getMembers(_sid);
    }


    /**
     * Add the user's team server ID to the ACL. No-op if there are other users on the shared folder
     * belonging to the same team server.
     */
    public ImmutableCollection<UserID> addTeamServerACL(User user)
            throws ExNotFound, ExAlreadyExist, SQLException
    {
        if (addTeamServerACLImpl(user)) {
            return _f._db.getMembers(_sid);
        } else {
            return ImmutableSet.of();
        }
    }

    /**
     * No-op if the team server ACL already exists
     *
     * @return whether actual operations are performed
     */
    private boolean addTeamServerACLImpl(User user)
            throws ExNotFound, SQLException, ExAlreadyExist
    {
        User tsUser = user.getOrganization().getTeamServerUser();
        if (getMemberRoleNullable(tsUser) == null) {
            SubjectRolePair srp = new SubjectRolePair(tsUser.id(), Role.EDITOR);
            _f._db.insertMemberACL(_sid, user.id(), Collections.singletonList(srp));
            return true;
        } else {
            return false;
        }
    }

    public ImmutableCollection<UserID> deleteMemberOrPendingACL(User user)
            throws SQLException, ExNotFound, ExNoPerm
    {
        // retrieve the list of affected users _before_ performing the deletion, so that all the
        // users including the deleted ones will get notifications.
        ImmutableCollection<UserID> members = _f._db.getMembers(_sid);

        _f._db.deleteMemberOrPendingACL(_sid, user.id());

        // delete the team server only if the user is a member
        if (members.contains(user.id())) {
            deleteTeamServerACLImpl(_f._factUser.create(user.id()));
        }

        throwIfNoOwnerMemberOrPendingLeft();

        return members;
    }

    /**
     * Remove the user's team server ID to the ACL. No-op if there are other users on the shared
     * folder belonging to the same team server.
     */
    public ImmutableCollection<UserID> deleteTeamServerACL(User user)
            throws SQLException, ExNotFound
    {
        ImmutableCollection<UserID> affectedUsers = _f._db.getMembers(_sid);

        if (deleteTeamServerACLImpl(user)) {
            return affectedUsers;
        } else {
            return ImmutableSet.of();
        }
    }

    /**
     * @return whether actual operations are performed.
     */
    private boolean deleteTeamServerACLImpl(User user)
            throws SQLException, ExNotFound
    {
        Organization org = user.getOrganization();

        for (User otherUser : getMembers()) {
            if (otherUser.equals(user)) continue;
            if (otherUser.id().isTeamServerID()) continue;
            if (otherUser.getOrganization().equals(org)) return false;
        }

        try {
            _f._db.deleteMemberOrPendingACL(_sid, org.id().toTeamServerUserID());
        } catch (ExNotFound e) {
            // the team server id must exists.
            assert false : this + " doesn't have team server ACL for " + user;
        }

        return true;
    }

    /**
     * N.B the return value include Team Servers
     */
    public ImmutableCollection<User> getMembers()
            throws SQLException
    {
        return userIDs2users(_f._db.getMembers(_sid));
    }

    public ImmutableCollection<User> userIDs2users(Collection<UserID> userIDs)
    {
        Builder<User> builder = ImmutableList.builder();
        for (UserID userID : userIDs) builder.add(_f._factUser.create(userID));
        return builder.build();

    }

    /**
     * N.B the return value include Team Servers
     */
    public List<SubjectRolePair> getMemberACL() throws SQLException
    {
        return _f._db.getMemberACL(_sid);
    }

    /**
     * N.B the return value include Team Servers
     */
    public ImmutableCollection<User> getAllUsers() throws SQLException
    {
        return userIDs2users(_f._db.getAllUsers(_sid));
    }

    /**
     * @return new ACL epochs for each affected user id, to be published via verkehr
     * @throws ExNotFound if trying to add new users to the store or update a pending user's ACL
     */
    public ImmutableCollection<UserID> updateMemberACL(User user, Role role)
            throws ExNoPerm, ExNotFound, SQLException
    {
        _f._db.updateMemberACL(_sid, user.id(), role);

        throwIfNoOwnerMemberOrPendingLeft();

        return _f._db.getMembers(_sid);
    }

    public @Nullable Role getMemberRoleNullable(User user)
            throws SQLException
    {
        return _f._db.getMemberRoleNullable(_sid, user.id());
    }

    /**
     * @throws ExNoPerm if the user is pending or not found
     */
    public @Nonnull Role getMemberRoleThrows(User user)
            throws SQLException, ExNoPerm
    {
        Role role = getMemberRoleNullable(user);
        if (role == null) throw new ExNoPerm();
        return role;
    }

    private void throwIfNoOwnerMemberOrPendingLeft()
            throws ExNoPerm, SQLException
    {
        if (!_f._db.hasOwnerMemberOrPending(_sid)) {
            throw new ExNoPerm("there must be at least one owner");
        }
    }

    /**
     * A user has privileges to chagne ACLs if and only if:
     *  1. the user is the owner of the folder, or
     *  2. the user is the team admin of at least one non-pending owner of the folder.
     */
    public void throwIfNoPrivilegeToChangeACL(User user)
            throws SQLException, ExNoPerm, ExNotFound
    {
        // Bypass the following expensive tests for common cases.
        if (isOwner(user)) return;

        // See if the user is the team admin of a non-pending owner of the folder.
        if (user.isAdmin()) {
            Organization org = user.getOrganization();
            for (SubjectRolePair srp: getMemberACL()) {
                if (srp._role.covers(Role.OWNER)) {
                    User member = _f._factUser.create(srp._subject);
                    if (member.getOrganization().equals(org)) return;
                }
            }
        }

        throw new ExNoPerm();
    }

    private boolean isOwner(User user)
            throws SQLException
    {
        Role role = getMemberRoleNullable(user);
        return role != null && role.covers(Role.OWNER);
    }

    public boolean isMember(User user) throws SQLException
    {
        return _f._db.getMemberRoleNullable(_sid, user.id()) != null;
    }

    public boolean isPending(User user) throws SQLException
    {
        return _f._db.getPendingRoleNullable(_sid, user.id()) != null;
    }

    public boolean isMemberOrPending(User user) throws SQLException
    {
        return _f._db.getMemberOrPendingRoleNullable(_sid, user.id()) != null;
    }
}
