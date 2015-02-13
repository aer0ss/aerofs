/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.acl.ACLFilter;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.cfg.Cfg;
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

    @Inject
    public MultiuserACLFilter(IACLDatabase adb)
    {
        super(adb);
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
        // don't filter anything if the list of users is empty
        // single member -> store created on TS -> should not be filtered out
        if (Cfg.usersInShard().isEmpty() || users.size() == 1) return true;

        for (UserID user : Cfg.usersInShard()) {
            if (users.contains(user)) return true;
        }
        return false;
    }
}
