/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.event.Prio;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

// [sigh]
//
// I hate the fact that I had to _COPY AND PASTE_ the damn test three times,
// but using PowerMockito makes it very hard to do the right thing (and I have
// to use PowerMockito due to TransportProtocolUtil
//
// the right thing to do is use ChannelTeardownHandler in TCP, Jingle and Zephyr and then
// inline the TransportProtocolUtil.sessionEnded method into ChannelTeardownHandler (thus removing
// the need for PowerMockito entirely, allowing me to use Parameterized runners, etc.)
//
// till then, this will have to do, and I will live with this shame

// I have to put PowerMockIgnore here because of this:
// http://stackoverflow.com/questions/8179399/javax-xml-parsers-saxparserfactory-classcastexception
public final class TestChannelTeardownHandler
{
    static
    {
        LoggerSetup.init();
    }

    // I can't mock out any objects here because of this bug:
    // https://code.google.com/p/powermock/issues/detail?id=414

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
        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                closeFuture.setSuccess();
                return null;
            }
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
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                closeFuture.setSuccess();
                return null;
            }
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
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                closeFuture.setSuccess();
                return null;
            }
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
