/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.ids.SID;

/**
 * Shared Folder Operation indicating renaming of Shared Folder
 * Triggered by renaming corresponding folder in File System
 *
 */
public class RenameOp implements ISharedFolderOp
{
    private final SID sid;
    private final String name;


    public RenameOp(SID sid, String name) {
        this.sid = sid;
        this.name = name;
    }

    @Override
    public SID getSID()
    {
        return sid;
    }

    @Override
    public SharedFolderOpType getType()
    {
        return SharedFolderOpType.RENAME;
    }

    public String getName()
    {
        return name;
    }

    @Override
    public String toString()
    {
        return "Rename to: " + name + "; sid=" + sid;
    }

}
