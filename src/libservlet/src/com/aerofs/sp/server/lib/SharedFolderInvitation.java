/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.SharedFolder;

public class SharedFolderInvitation
{
    private final UserID _sharer;
    private final UserID _sharee;
    private final SharedFolder _sf;

    public SharedFolderInvitation(UserID sharer, UserID sharee, SharedFolder sf)
    {
        _sf = sf;
        _sharer = sharer;
        _sharee = sharee;
    }

    public SharedFolder folder()
    {
        return _sf;
    }

    public UserID sharer()
    {
        return _sharer;
    }

    public UserID sharee()
    {
        return _sharee;
    }

    @Override
    public int hashCode()
    {
        return _sf.hashCode() ^ _sharer.hashCode() ^ _sharee.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this) return true;
        if (o == null) return false;
        SharedFolderInvitation sfi = (SharedFolderInvitation)o;
        return sfi._sf.equals(_sf) && sfi._sharer.equals(_sharer) && sfi._sharee.equals(_sharee);
    }

    @Override
    public String toString()
    {
        return "SharedFolderInvitation(" + _sharer + ", " + _sharee + ", " + _sf + ")";
    }
}
