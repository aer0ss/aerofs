/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.testlib.LoggerSetup;
import com.aerofs.daemon.transport.lib.ChannelData;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.channel.DefaultExceptionEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.UpstreamChannelStateEvent;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public final class TestChannelTeardownHandler
{
    static
    {
        LoggerSetup.init();
    }

    private IBlockingPrioritizedEventSink<IEvent> outgoingEventSink = spy(new BlockingPrioQueue<IEvent>(100));
    private StreamManager streamManager = spy(new StreamManager());

    @Test
    public void shouldCloseOutboundPeerStreamsWhenChannelCloseIsCalled()
            throws Exception
    {
        ITransport transport = mock(ITransport.class);

        final Channel channel = mock(Channel.class);
        final ChannelFuture closeFuture = new DefaultChannelFuture(channel, false);

        DID did = DID.generate();
        when(channel.getCloseFuture()).thenReturn(closeFuture);
        when(channel.getAttachment()).thenReturn(new ChannelData(UserID.DUMMY, did));
        doAnswer(invocation -> {
            closeFuture.setSuccess();
            return null;
        }).when(channel).close();

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(channel);

        ChannelTeardownHandler handler = new ChannelTeardownHandler(transport, outgoingEventSink, streamManager, ChannelMode.CLIENT);
        handler.channelOpen(ctx, new UpstreamChannelStateEvent(channel, ChannelState.OPEN, Boolean.TRUE));
        verify(ctx).sendUpstream(any(ChannelEvent.class));

        channel.close();

        verify(streamManager).removeAllOutgoingStreams(did);
        verify(streamManager, never()).removeAllIncomingStreams(did);
        verifyZeroInteractions(outgoingEventSink);
    }

    @Test
    public void shouldCloseInboundPeerStreamsWhenChannelCloseIsCalled()
            throws Exception
    {
        ITransport transport = mock(ITransport.class);

        final Channel channel = mock(Channel.class);
        final ChannelFuture closeFuture = new DefaultChannelFuture(channel, false);

        DID did = DID.generate();
        when(channel.getCloseFuture()).thenReturn(closeFuture);
        when(channel.getAttachment()).thenReturn(new ChannelData(UserID.DUMMY, did));
        doAnswer(invocation -> {
            closeFuture.setSuccess();
            return null;
        }).when(channel).close();

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(channel);

        ChannelTeardownHandler handler = new ChannelTeardownHandler(transport, outgoingEventSink, streamManager, ChannelMode.SERVER);
        handler.channelOpen(ctx, new UpstreamChannelStateEvent(channel, ChannelState.OPEN, Boolean.TRUE));
        verify(ctx).sendUpstream(any(ChannelEvent.class));

        channel.close();

        verify(streamManager, never()).removeAllOutgoingStreams(did);
        verify(streamManager).removeAllIncomingStreams(did);
        verifyZeroInteractions(outgoingEventSink);
    }

    @Test
    public void shouldCloseBothInboundAndOutboundPeerStreamsWhenChannelCloseIsCalled()
            throws Exception
    {
        ITransport transport = mock(ITransport.class);

        final Channel channel = mock(Channel.class);
        final ChannelFuture closeFuture = new DefaultChannelFuture(channel, false);

        DID did = DID.generate();
        when(channel.getCloseFuture()).thenReturn(closeFuture);
        when(channel.getAttachment()).thenReturn(new ChannelData(UserID.DUMMY, did));
        doAnswer(invocation -> {
            closeFuture.setSuccess();
            return null;
        }).when(channel).close();

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(channel);

        ChannelTeardownHandler handler = new ChannelTeardownHandler(transport, outgoingEventSink, streamManager, ChannelMode.TWOWAY);
        handler.channelOpen(ctx, new UpstreamChannelStateEvent(channel, ChannelState.OPEN, Boolean.TRUE));
        verify(ctx).sendUpstream(any(ChannelEvent.class));

        channel.close();

        verify(streamManager).removeAllOutgoingStreams(did);
        verify(streamManager).removeAllIncomingStreams(did);
        verifyZeroInteractions(outgoingEventSink);
    }

    @Test
    public void shouldCloseChannelfChannelThrowsAnException()
            throws Exception
    {
        ITransport transport = mock(ITransport.class);
        ChannelTeardownHandler channelTeardownHandler = new ChannelTeardownHandler(transport, outgoingEventSink, streamManager, ChannelMode.TWOWAY);

        Channel channel = mock(Channel.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(channel);
        ExceptionEvent e = new DefaultExceptionEvent(channel, new IllegalStateException("I broke it"));

        channelTeardownHandler.exceptionCaught(ctx, e);
        verify(channel).close();
    }
}
