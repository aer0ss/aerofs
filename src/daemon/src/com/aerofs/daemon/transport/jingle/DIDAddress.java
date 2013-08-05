package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.id.DID;

import java.net.SocketAddress;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class wraps a DID and pretends its a SocketAddress.
 * This is necessary because Netty pipelines think in terms of SocketAddress, but we think in terms
 * of DIDs. See org.jboss.netty.channel.local.LocalAddress for a similar implementation that uses
 * strings for ids.
 */
public class DIDAddress extends SocketAddress
{
    private static final long serialVersionUID = 1;
    private final DID _did;

    public DIDAddress(DID did)
    {
        _did = checkNotNull(did);
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
}
