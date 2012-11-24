package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.UserID;

/**
 * N.B. The ep().tp() field is ignored by equals() and hashCode().
 */
public class PeerContext
{
    private final Endpoint _ep;
    private final SIndex _sidx;
    private UserID _user;

    public PeerContext(Endpoint ep, SIndex sidx)
    {
        _ep = ep;
        _sidx = sidx;
    }

    /**
     * this method is a shortcut that avoids calls to DID2User for messages that
     * come from secure channels. call this method only at the receiver side,
     * and only at layers above DTLS.
     */
    public UserID user()
    {
        assert _user != null;
        return _user;
    }

    /**
     * only call this method after the user id is fully authenticated
     */
    public void setUser(UserID user)
    {
        _user = user;
    }

    public Endpoint ep()
    {
        return _ep;
    }

    public DID did()
    {
        return ep().did();
    }

    public ITransport tp()
    {
        return ep().tp();
    }

    public SIndex sidx()
    {
        return _sidx;
    }

    @Override
    public int hashCode()
    {
        return _ep.hashCode() + _sidx.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null &&
                ((PeerContext) o)._sidx.equals(_sidx) &&
                ((PeerContext) o)._ep.equals(_ep));
    }

    @Override
    public String toString()
    {
        return "[" + _ep + " " + _sidx + " "  + _user + "]";
    }
}
