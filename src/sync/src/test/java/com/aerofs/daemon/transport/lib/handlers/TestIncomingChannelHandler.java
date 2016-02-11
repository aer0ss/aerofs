/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.testlib.LoggerSetup;
import com.aerofs.daemon.transport.lib.ChannelData;
import com.aerofs.daemon.transport.lib.ChannelRegisterer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.UpstreamChannelStateEvent;
import org.junit.Test;
import org.mockito.InOrder;

import java.net.InetSocketAddress;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TestIncomingChannelHandler
{
    static
    {
        LoggerSetup.init();
    }

    private final ChannelRegisterer incomingChannelListener = mock(ChannelRegisterer.class);
    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
    private final Channel channel = mock(Channel.class);
    private final ChannelData channelData = new ChannelData(UserID.DUMMY, DID.generate());
    private final RegisteringChannelHandler handler = new RegisteringChannelHandler(incomingChannelListener);
    private final ChannelStateEvent connectedEvent = new UpstreamChannelStateEvent(channel, ChannelState.CONNECTED, InetSocketAddress.createUnresolved("boo", 1337));

    @Test
    public void shouldNotifyIncomingChannelListenerAndForwardConnectedEventOnWhenChannelConnectedEventReceived()
            throws Exception
    {
        when(channel.getAttachment()).thenReturn(channelData);
        when(incomingChannelListener.registerChannel(channel, channelData.getRemoteDID())).thenReturn(true);
        handler.channelConnected(ctx, connectedEvent);

        InOrder inOrder = inOrder(ctx, incomingChannelListener);
        inOrder.verify(incomingChannelListener).registerChannel(channel, channelData.getRemoteDID());
        inOrder.verify(ctx).sendUpstream(connectedEvent);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowIfChannelDoesNotHaveAChannelDataAttachment()
            throws Exception
    {
        handler.channelConnected(ctx, connectedEvent);
    }
}
