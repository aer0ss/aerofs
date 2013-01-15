/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
     */
    public Set<UserID> save(String folderName, User owner)
            throws ExNotFound, SQLException, IOException, ExAlreadyExist
    {
        _f._db.insert(_sid, folderName);

        try {
            return addACL(owner, Role.OWNER);
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
    public Set<UserID> addACL(User user, Role role)
            throws ExAlreadyExist, SQLException, ExNotFound
    {
        if (isMemberOrInvited(user)) throw new ExAlreadyExist(user + " is already invited");

        _f._db.insertACL(_sid, user.id(),
                Collections.singletonList(new SubjectRolePair(user.id(), role)));

        addTeamServerACLImpl(user);

        return _f._db.getACLUsers(_sid);
    }

    public void addPendingACL(User sharer, User sharee, Role role)
            throws SQLException, ExAlreadyExist
    {
        if (isMemberOrInvited(sharee)) throw new ExAlreadyExist(sharee + " is already invited");

        _f._db.insertPendingACL(_sid, sharer.id(),
                Collections.singletonList(new SubjectRolePair(sharee.id(), role)));
    }

    public Set<UserID> setPending(User user) throws SQLException, ExNotFound
    {
        Set<UserID> affectedUsers = _f._db.getACLUsers(_sid);

        _f._db.setPending(_sid, user.id(), true);

        // in terms of ACL, marking a user as pending is the same as kicking him out of the folder
        // so we need to update team server ACLs to reflect that change
        deleteTeamServerACLImpl(user);

        return affectedUsers;
    }

    public Set<UserID> resetPending(User user) throws SQLException, ExNotFound, ExAlreadyExist
    {
        _f._db.setPending(_sid, user.id(), false);

        addTeamServerACLImpl(user);

        return _f._db.getACLUsers(_sid);
    }


    /**
     * Add the user's team server ID to the ACL. No-op if the user belongs to the default org or
     * there are other users on the shared folder belonging to the same team server.
     */
    public Set<UserID> addTeamServerACL(User user)
            throws ExNotFound, ExAlreadyExist, SQLException
    {
        if (addTeamServerACLImpl(user)) {
            return _f._db.getACLUsers(_sid);
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * @return whether actual operations are performed
     */
    private boolean addTeamServerACLImpl(User user)
            throws ExNotFound, SQLException, ExAlreadyExist
    {
        Organization org = user.getOrganization();
        if (org.isDefault()) return false;

        User tsUser = _f._factUser.create(org.id().toTeamServerUserID());
        if (getRoleNullable(tsUser) == null) {
            SubjectRolePair srp = new SubjectRolePair(tsUser.id(), Role.EDITOR);
            _f._db.insertACL(_sid, user.id(), Collections.singletonList(srp));
            return true;
        } else {
            return false;
        }
    }

    public Set<UserID> deleteACL(Collection<UserID> subjects)
            throws SQLException, ExNotFound, ExNoPerm
    {
        // retrieve the list of affected users _before_ performing the deletion, so that all the
        // users including the deleted ones will get notifications.
        Set<UserID> affectedUsers = _f._db.getACLUsers(_sid);

        _f._db.deleteACL(_sid, subjects);

        for (UserID userID : subjects) {
            deleteTeamServerACLImpl(_f._factUser.create(userID));
        }

        throwIfNoOwnerLeft();

        return affectedUsers;
    }

    /**
     * Remove the user's team server ID to the ACL. No-op if the user belongs to the default org or
     * there are other users on the shared folder belonging to the same team server.
     */
    public Set<UserID> deleteTeamServerACL(User user)
            throws SQLException, ExNotFound
    {
        Set<UserID> affectedUsers = _f._db.getACLUsers(_sid);

        if (deleteTeamServerACLImpl(user)) {
            return affectedUsers;
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * @return whether actual operations are performed.
     */
    private boolean deleteTeamServerACLImpl(User user)
            throws SQLException, ExNotFound
    {
        Organization org = user.getOrganization();
        if (org.isDefault()) return false;

        for (User otherUser : getUsers()) {
            if (otherUser.equals(user)) continue;
            if (otherUser.id().isTeamServerID()) continue;
            if (otherUser.getOrganization().equals(org)) return false;
        }

        try {
            _f._db.deleteACL(_sid, Collections.singleton(org.id().toTeamServerUserID()));
        } catch (ExNotFound e) {
            // the team server id must exists.
            assert false : this + " " + user;
        }

        return true;
    }

    public Collection<User> getUsers()
            throws SQLException
    {
        Builder<User> builder = ImmutableList.builder();
        for (UserID userID : _f._db.getACLUsers(_sid)) {
            builder.add(_f._factUser.create(userID));
        }
        return builder.build();
    }

    public List<SubjectRolePair> getACL() throws SQLException
    {
        return _f._db.getACL(_sid);
    }

    /**
     * @return new ACL epochs for each affected user id, to be published via verkehr
     * @throws ExNotFound if trying to add new users to the store
     */
    public Set<UserID> updateACL(List<SubjectRolePair> srps)
            throws ExNoPerm, ExNotFound, SQLException
    {
        _f._db.updateACL(_sid, srps);

        throwIfNoOwnerLeft();

        return _f._db.getACLUsers(_sid);
    }

    public @Nullable Role getRoleNullable(User user)
            throws SQLException
    {
        return _f._db.getRoleNullable(_sid, user.id());
    }

    /**
     * @throws ExNoPerm if the user is not found
     */
    public @Nonnull Role getRoleThrows(User user)
            throws SQLException, ExNoPerm
    {
        Role role = getRoleNullable(user);
        if (role == null) throw new ExNoPerm();
        return role;
    }

    private void throwIfNoOwnerLeft()
            throws ExNoPerm, SQLException
    {
        if (!_f._db.hasOwner(_sid)) throw new ExNoPerm("there must be at least one owner");
    }

    public void throwIfNotOwnerAndNotAdmin(User user)
            throws SQLException, ExNoPerm, ExNotFound
    {
        if (!isOwner(user) && !user.isAdmin()) throw new ExNoPerm();
    }

    private boolean isOwner(User user)
            throws SQLException
    {
        Role role = getRoleNullable(user);
        return role != null && role.covers(Role.OWNER);
    }

    public boolean isMember(User user) throws SQLException
    {
        return _f._db.getRoleNullable(_sid, user.id()) != null;
    }

    public boolean isInvited(User user) throws SQLException
    {
        return _f._db.getPendingRoleNullable(_sid, user.id()) != null;
    }

    public boolean isMemberOrInvited(User user) throws SQLException
    {
        return _f._db.getRoleOrPendingNullable(_sid, user.id()) != null;
    }
}
