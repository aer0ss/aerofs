/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.acl.ACLFilter;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.UserID;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Set;

public class MultiuserACLFilter extends ACLFilter
{
    /**
     * For the TS, we need to bypass the epoch-based optimization
     * of ACL fetch on startup to make sure changes to the shard
     * configuration are acted upon immediately.
     *
     * To that end, we simply bypass the DB entirely for epoch
     * reads, which conveniently ensures a reset on startup.
     */
    private long _epoch = 0;
    private final UsersShard _userList;

    @Inject
    public MultiuserACLFilter(IACLDatabase adb, UsersShard userList)
    {
        super(adb);
        this._userList = userList;
    }

    @Override
    public long getEpoch_() throws SQLException
    {
        return _epoch;
    }

    @Override
    public void updateEpoch_(long epoch, Trans t) throws SQLException
    {
        _epoch = epoch;
        super.updateEpoch_(epoch, t);
    }

    @Override
    public boolean shouldKeep_(Set<UserID> users)
    {
        // All stores have at least 1 member: the TS user.
        // All stores created by a regular user have at least 1 regular user member with OWNER access.
        // Therefore, the only stores that have a single member are external stores created on a linked TS.
        // Per-user sharding should not mess with these.
        if (users.size() == 1 || !_userList.isSharded()) {
            return true;
        }

        // The CSV file is the list of users given by the admin and if given, it
        // should exist in rtroot.
        // Resulting user set is the union set of the two.
        for (UserID user : users) {
            if (_userList.contains(user)) return true;
        }
        return false;
    }
}
