/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.havre.tunnel;

import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.UserID;
import com.aerofs.havre.Version;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.testlib.AbstractBaseTest;
import com.aerofs.tunnel.TunnelAddress;
import com.aerofs.tunnel.TunnelHandler;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestTunnelEndpointConnector extends AbstractBaseTest
{
    protected static final AuthenticatedPrincipal user = newPrincipal("foo@bar.baz");
    protected static final AuthenticatedPrincipal ts =
            newPrincipal(OrganizationID.PRIVATE_ORGANIZATION.toTeamServerUserID().getString());
    protected static final DID did = DID.generate();

    @Mock EndpointVersionDetector detector;
    private TunnelEndpointConnector connector;

    private static AuthenticatedPrincipal newPrincipal(String userid)
    {
        AuthenticatedPrincipal p = new AuthenticatedPrincipal(userid);
        p.setEffectiveUserID(UserID.fromInternal(userid));
        p.setOrganizationID(OrganizationID.PRIVATE_ORGANIZATION);
        return p;
    }

    @Before
    public void setUp()
    {
        connector = new TunnelEndpointConnector(detector);
    }

    void connectClient(AuthenticatedPrincipal user, DID did) throws Exception
    {
        connectClient(user, did, new Version(1, 0));
    }

    TunnelHandler connectClient(AuthenticatedPrincipal user, DID did, final Version version) throws Exception
    {
        final TunnelAddress addr = new TunnelAddress(user.getEffectiveUserID(), did);
        TunnelHandler handler = mock(TunnelHandler.class);
        when(handler.address()).thenReturn(addr);
        when(handler.newVirtualChannel(any(ChannelPipeline.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable
                    {
                        Channel c = mock(Channel.class);
                        when(c.getRemoteAddress()).thenReturn(addr);
                        when(detector.detectHighestSupportedVersion(c))
                                .thenReturn(UncancellableFuture.createSucceeded(version));
                        return c;
                    }
                });
        connector.tunnelOpen(addr, handler);
        return handler;
    }

    Channel connect(AuthenticatedPrincipal user, DID did, boolean strictMatch, Version version)
    {
        return connector.connect(user, did, strictMatch, version, Channels.pipeline());
    }

    @Test
    public void shouldFailWhenNoDeviceAvailable() throws Exception
    {
        assertNull(connect(user, did, false, null));
    }

    @Test
    public void shouldFailWhenNoDeviceAvailableForUser() throws Exception
    {
        connectClient(newPrincipal("somebodyiusedtoknow"), did);
        assertNull(connect(user, did, false, null));
    }

    @Test
    public void shouldSucceedWhenRequestedDeviceAvailable() throws Exception
    {
        connectClient(user, did);
        assertOneOf(connect(user, did, false, null), did);
    }

    @Test
    public void shouldSucceedWhenRequestedDeviceAndHigherVersionAvailable() throws Exception
    {
        connectClient(user, did, new Version(1, 2));
        assertOneOf(connect(user, null, false, new Version(1, 0)), did);
    }

    @Test
    public void shouldSucceedWhenRequestedDeviceAndExactVersionAvailable() throws Exception
    {
        connectClient(user, did, new Version(1, 0));
        assertOneOf(connect(user, null, false, new Version(1, 0)), did);
    }

    @Test
    public void shouldFailWhenNoDeviceAvailableForUserAndVersion() throws Exception
    {
        connectClient(user, did, new Version(1, 0));
        assertNull(connect(user, null, false, new Version(1, 1)));
    }

    @Test
    public void shouldFailWhenPreferredDeviceHasLowerVersion() throws Exception
    {
        connectClient(user, did, new Version(1, 0));
        assertNull(connect(user, did, false, new Version(1, 1)));
    }

    @Test
    public void shouldSucceedWhenDifferentDeviceAvailableAndNotStrictMatch() throws Exception
    {
        connectClient(user, did);
        assertOneOf(connect(user, DID.generate(), false, null), did);
    }

    @Test
    public void shouldSucceedWhenDifferentDeviceAndVersionAvailableAndNotStrictMatch() throws Exception
    {
        connectClient(user, did);
        assertOneOf(connect(user, DID.generate(), false, new Version(0, 9)), did);
    }

    @Test
    public void shouldFailWhenDifferentDeviceAvailableAndStrictMatch() throws Exception
    {
        connectClient(user, did);
        assertNull(connect(user, DID.generate(), true, null));
    }

    @Test
    public void shouldPickRandomlyAmongMatchingEndpoints() throws Exception
    {
        DID d0, d1, d2;
        connectClient(user, d0 = DID.generate(), new Version(0, 9));
        connectClient(user, d1 = DID.generate(), new Version(1, 1));
        connectClient(user, d2 = DID.generate(), new Version(2, 0));

        assertOneOf(connect(user, null, false, new Version(0, 5)), d0, d1, d2);

        assertOneOf(connect(user, null, false, new Version(0, 9)), d0, d1, d2);
        assertOneOf(connect(user, d0, false, new Version(0, 9)), d0);
        assertOneOf(connect(user, d1, false, new Version(0, 9)), d1);
        assertOneOf(connect(user, d2, false, new Version(0, 9)), d2);

        assertOneOf(connect(user, null, false, new Version(1, 0)), d1, d2);
        assertOneOf(connect(user, d0, false, new Version(1, 1)), d1, d2);

        assertOneOf(connect(user, null, false, new Version(1, 1)), d1, d2);
        assertOneOf(connect(user, d0, false, new Version(1, 1)), d1, d2);
        assertOneOf(connect(user, d1, false, new Version(1, 1)), d1);
        assertOneOf(connect(user, d2, false, new Version(1, 1)), d2);

        assertOneOf(connect(user, null, false, new Version(1, 5)), d2);
        assertOneOf(connect(user, d0, false, new Version(1, 5)), d2);
        assertOneOf(connect(user, d1, false, new Version(1, 5)), d2);
        assertOneOf(connect(user, d2, false, new Version(1, 5)), d2);

        assertOneOf(connect(user, null, false, new Version(2, 0)), d2);
        assertOneOf(connect(user, d0, false, new Version(2, 0)), d2);
        assertOneOf(connect(user, d1, false, new Version(2, 0)), d2);
        assertOneOf(connect(user, d2, false, new Version(2, 0)), d2);
    }

    private void assertOneOf(Channel c, DID... dids)
    {
        assertThat(connector.device(c), isIn(dids));
    }

    @Test
    public void shouldPickNewestTunnelForDID() throws Exception
    {
        DID d0 = DID.generate();
        TunnelHandler h0 = connectClient(user, d0, new Version(0, 9));
        TunnelHandler h1 = connectClient(user, d0, new Version(0, 9));

        ChannelPipeline pipeline = Channels.pipeline();
        Channel c = connector.connect(user, null, false, null, pipeline);
        assertNotNull(c);
        verify(h0, never()).newVirtualChannel(pipeline);
        verify(h1).newVirtualChannel(pipeline);
    }

    @Test
    public void shouldNotPickPreviousTunnelForDID() throws Exception
    {
        DID d0 = DID.generate();
        TunnelHandler h0 = connectClient(user, d0, new Version(0, 9));
        TunnelHandler h1 = connectClient(user, d0, new Version(0, 9));
        connector.tunnelClosed(h0.address(), h0);

        ChannelPipeline pipeline = Channels.pipeline();
        Channel c = connector.connect(user, null, false, null, pipeline);
        assertNotNull(c);
        verify(h0, never()).newVirtualChannel(pipeline);
        verify(h1).newVirtualChannel(pipeline);
    }

    @Test
    public void shouldPickNewestTunnelForDIDDifferentVersion() throws Exception
    {
        DID d0 = DID.generate();
        TunnelHandler h0 = connectClient(user, d0, new Version(0, 9));
        TunnelHandler h1 = connectClient(user, d0, new Version(0, 10));

        ChannelPipeline pipeline = Channels.pipeline();
        Channel c = connector.connect(user, null, false, null, pipeline);
        assertNotNull(c);
        verify(h0, never()).newVirtualChannel(pipeline);
        verify(h1).newVirtualChannel(pipeline);
    }

    @Test
    public void shouldPickTeamServerOverRegularClient() throws Exception
    {
        TunnelHandler h0 = connectClient(ts, DID.generate(), new Version(0, 10));
        TunnelHandler h1 = connectClient(user, did, new Version(0, 10));

        ChannelPipeline pipeline = Channels.pipeline();
        Channel c = connector.connect(user, null, false, null, pipeline);
        assertNotNull(c);
        verify(h0).newVirtualChannel(pipeline);
        verify(h1, never()).newVirtualChannel(pipeline);
    }
}
