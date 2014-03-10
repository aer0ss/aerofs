/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.google.common.base.Objects;

public final class ChannelData implements IChannelData
{
    private final UserID userID;
    private final DID did;

    public ChannelData(UserID userID, DID did)
    {
        this.userID = userID;
        this.did = did;
    }

    @Override
    public DID getRemoteDID()
    {
        return did;
    }

    @Override
    public UserID getRemoteUserID()
    {
        return userID;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChannelData other = (ChannelData) o;
        return did.equals(other.did) && userID.equals(other.userID);
    }

    @Override
    public int hashCode()
    {
        int result = userID.hashCode();
        result = 31 * result + did.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this).add("user", userID).add("did", did).toString();
    }
}
