/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.havre.tunnel;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.tunnel.TunnelAddress;
import com.aerofs.tunnel.TunnelHandler;
import org.jboss.netty.channel.Channels;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TestTunnelEndpointConnector
{
    protected static final UserID user = UserID.fromInternal("foo@bar.baz");
    protected static final DID did = DID.generate();
    private TunnelEndpointConnector connector;


    @Before
    public void setUp()
    {
        connector = new TunnelEndpointConnector();
    }

    void connectClient(UserID user, DID did) throws Exception
    {
        connector.tunnelOpen(new TunnelAddress(user, did), new TunnelHandler(null, null));
    }

    @Test
    public void shouldFailWhenNoDeviceAvailableForUser() throws Exception
    {
        assertNull(connector.connect(user, did, false, Channels.pipeline()));
    }

    @Test
    public void shouldSucceedWhenRequestedDeviceAvailable() throws Exception
    {
        connectClient(user, did);
        assertNotNull(connector.connect(user, did, false, Channels.pipeline()));
    }

    @Test
    public void shouldSucceedWhenDifferentDeviceAvailableAndNotStrictMatch() throws Exception
    {
        connectClient(user, did);
        assertNotNull(connector.connect(user, DID.generate(), false, Channels.pipeline()));
    }

    @Test
    public void shouldFailWhenDifferentDeviceAvailableAndStrictMatch() throws Exception
    {
        connectClient(user, did);
        assertNull(connector.connect(user, DID.generate(), true, Channels.pipeline()));
    }
}
