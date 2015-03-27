/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.google.common.base.Objects;

public final class ChannelData
{
    private final UserID userID;
    private final DID did;

    public ChannelData(UserID userID, DID did)
    {
        this.userID = userID;
        this.did = did;
    }

    public final DID getRemoteDID()
    {
        return did;
    }

    public final UserID getRemoteUserID()
    {
        return userID;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == null || !(o instanceof ChannelData)) return false;
        if (this == o) return true;

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
