/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.IAddressResolver;

import java.net.SocketAddress;

/**
 * An implementation of {@code IAddressResolver} that
 * returns a {@link com.aerofs.daemon.transport.jingle.JingleAddress} when
 * given a {@link com.aerofs.base.id.DID}.
 */
final class JingleAddressResolver implements IAddressResolver
{
    private final String xmppServerDomain;

    JingleAddressResolver(String xmppServerDomain)
    {
        this.xmppServerDomain = xmppServerDomain;
    }

    @Override
    public SocketAddress resolve(DID did)
            throws ExDeviceUnavailable
    {
        return new JingleAddress(did, JingleUtils.did2jid(did, xmppServerDomain));
    }
}
