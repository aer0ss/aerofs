/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ITransport;

public final class EndpointFactory
{
    private final ITransport transport;

    public EndpointFactory(ITransport transport)
    {
        this.transport = transport;
    }

    public Endpoint newEndpoint(DID did)
    {
        return new Endpoint(transport, did);
    }
}
