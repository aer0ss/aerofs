/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.acl;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Set;

public class ACLFilter
{
    protected final static Logger l = Loggers.getLogger(ACLFilter.class);

    private final IACLDatabase _adb;

    @Inject
    public ACLFilter(IACLDatabase adb)
    {
        _adb = adb;
    }

    public long getEpoch_() throws SQLException
    {
        return _adb.getEpoch_();
    }

    public void updateEpoch_(long epoch, Trans t)
            throws SQLException
    {
        _adb.setEpoch_(epoch, t);
    }

    public boolean shouldKeep_(Set<UserID> users)
    {
        return true;
    }
}
