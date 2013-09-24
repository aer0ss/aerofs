package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.id.DID;
import com.aerofs.j.Jid;

import java.net.SocketAddress;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class wraps a DID and its associated JID and pretends its a SocketAddress.
 * This is necessary because Netty pipelines think in terms of SocketAddress, but we think in terms
 * of DIDs (and Jingle thinks in terms of JIDs). See org.jboss.netty.channel.local.LocalAddress
 * for a similar implementation that uses strings for ids.
 */
class JingleAddress extends SocketAddress
{
    private static final long serialVersionUID = 1;
    private final DID _did;
    private final Jid _jid;

    public JingleAddress(DID did, Jid jid)
    {
        _did = checkNotNull(did);
        _jid = checkNotNull(jid);
    }

    public DID getDid()
    {
        return _did;
    }

    @Override
    public int hashCode()
    {
        return _did.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return _did.equals(o);
    }

    @Override
    public String toString()
    {
        return _did.toString();
    }

    public Jid getJid()
    {
        return _jid;
    }
}
