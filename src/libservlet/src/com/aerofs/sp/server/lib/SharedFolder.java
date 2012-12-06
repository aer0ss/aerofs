/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

        public SharedFolder create_(ByteString sid)
        {
            return create_(new SID(sid));
        }

        public SharedFolder create_(SID sid)
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
    public Map<UserID, Long> add(String folderName, User owner)
            throws ExNoPerm, ExNotFound, ExAlreadyExist, SQLException, IOException
    {
        _f._db.add(_sid, folderName);

        return addACL(owner, Role.OWNER);
    }

    public void delete()
            throws SQLException
    {
        _f._db.delete(_sid);
    }

    /**
     * @return A map of user IDs to epochs to be published via verkehr.
     * @throws ExAlreadyExist if the user is already added.
     */
    public Map<UserID, Long> addACL(User user, Role role)
            throws ExAlreadyExist, SQLException, ExNotFound
    {
        if (getRoleNullable(user) != null) {
            // old invite/join workflow: ACL added on invite
            // TODO: remove this codepath after transition period...
            return Collections.emptyMap();
        } else {
            _f._db.addACL(_sid, Collections.singletonList(new SubjectRolePair(user.id(), role)));

            addTeamServerACLImpl(user);

            // increment ACL epoch for all users currently sharing the folder
            // making the modification to the database, and then getting the current acl list should
            // be done in a single atomic operation. Otherwise, it is possible for us to send out a
            // notification that is newer than what it should be (i.e. we skip an update
            return _f._db.incrementACLEpoch(_f._db.getACLUsers(_sid));
        }
    }

    /**
     * Add the user's team server ID to the ACL. No-op if the user belongs to the default org or
     * there are other users on the shared folder belonging to the same team server.
     */
    public Map<UserID, Long> addTeamServerACL(User user)
            throws ExNotFound, ExAlreadyExist, SQLException
    {
        if (addTeamServerACLImpl(user)) {
            return _f._db.incrementACLEpoch(_f._db.getACLUsers(_sid));
        } else {
            return Collections.emptyMap();
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
            _f._db.addACL(_sid, Collections.singletonList(srp));
            return true;
        } else {
            return false;
        }
    }

    public Map<UserID, Long> deleteACL(Collection<UserID> subjects)
            throws SQLException, ExNotFound, ExNoPerm
    {
        // retrieve the list of affected users _before_ performing the deletion, so that all the
        // users including the deleted ones will get notifications.
        Set<UserID> affectedUsers = _f._db.getACLUsers(_sid);

        _f._db.deleteACL(_sid, subjects);

        throwIfNoOwnerLeft();

        for (UserID userID : subjects) {
            deleteTeamServerACLImpl(_f._factUser.create(userID));
        }

        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update

        return _f._db.incrementACLEpoch(affectedUsers);
    }

    /**
     * Remove the user's team server ID to the ACL. No-op if the user belongs to the default org or
     * there are other users on the shared folder belonging to the same team server.
     */
    public Map<UserID, Long> deleteTeamServerACL(User user)
            throws SQLException, ExNotFound
    {
        Set<UserID> affectedUsers = _f._db.getACLUsers(_sid);

        if (deleteTeamServerACLImpl(user)) {
            return _f._db.incrementACLEpoch(affectedUsers);
        } else {
            return Collections.emptyMap();
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

        _f._db.deleteACL(_sid, Collections.singleton(org.id().toTeamServerUserID()));
        return true;
    }

    public Collection<User> getUsers()
            throws SQLException
    {
        List<User> users = Lists.newArrayList();
        for (UserID userID : _f._db.getACLUsers(_sid)) {
            users.add(_f._factUser.create(userID));
        }
        return users;
    }

    /**
     * @return new ACL epochs for each affected user id, to be published via verkehr
     * @throws ExNotFound if trying to add new users to the store
     */
    public Map<UserID, Long> updateACL(List<SubjectRolePair> srps)
            throws ExNoPerm, ExNotFound, SQLException
    {
        _f._db.updateACL(_sid, srps);

        throwIfNoOwnerLeft();

        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update

        return _f._db.incrementACLEpoch(_f._db.getACLUsers(_sid));
    }

    public @Nullable Role getRoleNullable(User user)
            throws SQLException
    {
        return _f._db.getRoleNullable(_sid, user.id());
    }

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
        if (!_f._db.hasOwner(_sid)) throw new ExNoPerm("cannot demote all admins");
    }

    public void throwIfNotOwner(User user)
            throws SQLException, ExNoPerm
    {
        Role role = getRoleNullable(user);
        if (role == null || !role.covers(Role.OWNER)) throw new ExNoPerm();
    }
}
