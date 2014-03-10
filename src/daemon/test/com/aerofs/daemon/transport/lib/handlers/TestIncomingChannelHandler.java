/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.transport.TransportLoggerSetup;
import com.aerofs.daemon.transport.lib.ChannelData;
import com.aerofs.daemon.transport.lib.IIncomingChannelListener;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.UpstreamChannelStateEvent;
import org.junit.Test;
import org.mockito.InOrder;

import java.net.InetSocketAddress;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TestIncomingChannelHandler
{
    static
    {
        TransportLoggerSetup.init();
    }

    private final IIncomingChannelListener incomingChannelListener = mock(IIncomingChannelListener.class);
    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
    private final Channel channel = mock(Channel.class);
    private final ChannelData channelData = new ChannelData(UserID.DUMMY, DID.generate());
    private final IncomingChannelHandler handler = new IncomingChannelHandler(incomingChannelListener);
    private final ChannelStateEvent connectedEvent = new UpstreamChannelStateEvent(channel, ChannelState.CONNECTED, InetSocketAddress.createUnresolved("boo", 1337));

    @Test
    public void shouldNotifyIncomingChannelListenerAndForwardConnectedEventOnWhenChannelConnectedEventReceived()
            throws Exception
    {
        when(channel.getAttachment()).thenReturn(channelData);

        handler.channelConnected(ctx, connectedEvent);

        InOrder inOrder = inOrder(ctx, incomingChannelListener);
        inOrder.verify(incomingChannelListener).onIncomingChannel(channelData.getRemoteDID(), channel);
        inOrder.verify(ctx).sendUpstream(connectedEvent);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowIfChannelDoesNotHaveAChannelDataAttachment()
            throws Exception
    {
        handler.channelConnected(ctx, connectedEvent);
    }
}
