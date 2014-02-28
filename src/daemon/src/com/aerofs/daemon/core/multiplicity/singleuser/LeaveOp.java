/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.id.SID;

/**
 * Shared Folder Operation indicating that user should leave shared folder
 * Typically triggered by removing corresponding folder in File System
 *
 */
public class LeaveOp implements ISharedFolderOp
{
    private final SID sid;

    public LeaveOp(SID sid) {
        this.sid = sid;
    }

    @Override
    public SID getSID()
    {
        return sid;
    }

    @Override
    public SharedFolderOpType getType()
    {
        return SharedFolderOpType.LEAVE;
    }

    @Override
    public String toString()
    {
        return "Leave: sid=" + sid;
    }
}
