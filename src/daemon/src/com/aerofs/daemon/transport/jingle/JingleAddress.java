package com.aerofs.daemon.transport.jingle;

import com.aerofs.ids.DID;
import com.aerofs.j.Jid;

import java.net.SocketAddress;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class wraps a DID and its associated JID and pretends its a SocketAddress.
 * This is necessary because Netty pipelines think in terms of SocketAddress, but we think in terms
 * of DIDs (and Jingle thinks in terms of JIDs). See org.jboss.netty.channel.local.LocalAddress
 * for a similar implementation that uses strings for ids.
 */
final class JingleAddress extends SocketAddress
{
    private static final long serialVersionUID = 1;

    private final DID did;
    private final Jid jid;

    JingleAddress(DID did, Jid jid)
    {
        this.did = checkNotNull(did);
        this.jid = checkNotNull(jid);
    }

    DID getDid()
    {
        return did;
    }

    Jid getJid()
    {
        return jid;
    }

    @Override
    public int hashCode()
    {
        return did.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return did.equals(o);
    }

    @Override
    public String toString()
    {
        return did.toString();
    }
}
