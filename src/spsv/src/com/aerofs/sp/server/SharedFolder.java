/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.SharedFolderDatabase;
import com.aerofs.sp.server.lib.user.User;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;

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
        private final SharedFolderDatabase _db;

        @Inject
        public Factory(SharedFolderDatabase db)
        {
            _db = db;
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

    public String getName(User user)
            throws ExNoPerm, ExNotFound, SQLException
    {
        if (_f._db.getRoleNullable(_sid, user.id()) == null) throw new ExNoPerm();

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
            throws ExAlreadyExist, SQLException
    {
        // TODO (WWW) reconsider permission check here.

        if (_f._db.getRoleNullable(_sid, user.id()) != null) {
            // old invite/join workflow: ACL added on invite
            // TODO: remove this codepath after transition period...
            return Collections.emptyMap();
        } else {
            _f._db.addACL(_sid, Collections.singletonList(new SubjectRolePair(user.id(), role)));

            // increment ACL epoch for all users currently sharing the folder
            // making the modification to the database, and then getting the current acl list should
            // be done in a single atomic operation. Otherwise, it is possible for us to send out a
            // notification that is newer than what it should be (i.e. we skip an update
            return _f._db.incrementACLEpoch(_f._db.getACLUsers(_sid));
        }
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

    public Map<UserID, Long> deleteACL(Collection<UserID> subjects)
            throws SQLException, ExNotFound, ExNoPerm
    {
        // retrieve the list of affected users _before_ performing the deletion, so that all the
        // users including the deleted ones will get notifications.
        Set<UserID> affectedUsers = _f._db.getACLUsers(_sid);

        _f._db.deleteACL(_sid, subjects);

        throwIfNoOwnerLeft();

        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update

        return _f._db.incrementACLEpoch(affectedUsers);
    }

    private void throwIfNoOwnerLeft()
            throws ExNoPerm, SQLException
    {
        if (!_f._db.hasOwner(_sid)) throw new ExNoPerm("cannot demote all admins");
    }

    public void throwIfNotOwner(User user)
            throws SQLException, ExNoPerm
    {
        Role role = _f._db.getRoleNullable(_sid, user.id());
        if (role == null || !role.covers(Role.OWNER)) throw new ExNoPerm();
    }
}
