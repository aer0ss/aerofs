package com.aerofs.tunnel;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.google.common.base.Objects;

import java.net.SocketAddress;

/**
 * A tunnel address is a (UserID, DID) pair.
 */
public class TunnelAddress extends SocketAddress
{
    private static final long serialVersionUID = 0L;

    public final UserID user;
    public final DID did;

    public TunnelAddress(UserID user, DID did)
    {
        this.user = user;
        this.did = did;
    }

    @Override
    public String toString()
    {
        return String.format("(%s, %s)",
                user != null? user.getString() : null,
                did != null ? did.toString() : null);
    }

    @Override
    public boolean equals(Object o)
    {
        return o != null && o instanceof TunnelAddress
                && user.equals(((TunnelAddress)o).user)
                && did.equals(((TunnelAddress)o).did);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(user, did);
    }
}
