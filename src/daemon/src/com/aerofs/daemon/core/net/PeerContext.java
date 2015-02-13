package com.aerofs.daemon.core.net;

import com.aerofs.ids.UserID;
import com.aerofs.daemon.event.net.Endpoint;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class bundles together an EndPoint and a UserID.
 * This is used only when receiving messages from secure (ie: authenticated) channels
 */
public class PeerContext
{
    private final Endpoint _ep;
    private final UserID _user;

    public PeerContext(Endpoint ep, UserID user)
    {
        _ep = checkNotNull(ep);
        _user = checkNotNull(user);
    }

    public UserID user()
    {
        return _user;
    }

    public Endpoint ep()
    {
        return _ep;
    }

    @Override
    public int hashCode()
    {
        return _ep.hashCode() + _user.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null
                                     && ((PeerContext)o)._ep.equals(_ep)
                                     && ((PeerContext)o)._user.equals(_user));
    }

    @Override
    public String toString()
    {
        return "[" + _ep + " "  + _user + "]";
    }
}
