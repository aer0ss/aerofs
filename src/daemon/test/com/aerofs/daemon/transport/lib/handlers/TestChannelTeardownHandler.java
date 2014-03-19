/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.TransportLoggerSetup;
import com.aerofs.daemon.transport.lib.ChannelData;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportProtocolUtil;
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
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
@RunWith(PowerMockRunner.class)
@PrepareForTest(TransportProtocolUtil.class)
@PowerMockIgnore({"javax.management.*", "javax.xml.parsers.*", "com.sun.org.apache.xerces.internal.jaxp.*", "ch.qos.logback.*", "org.slf4j.*"})
public final class TestChannelTeardownHandler
{
    static
    {
        TransportLoggerSetup.init();
    }

    // I can't mock out any objects here because of this bug:
    // https://code.google.com/p/powermock/issues/detail?id=414

    private IBlockingPrioritizedEventSink<IEvent> outgoingEventSink = new BlockingPrioQueue<IEvent>(100);
    private StreamManager streamManager = new StreamManager();

    @Test
    public void shouldCloseOutboundPeerStreamsWhenChannelCloseIsCalled()
            throws Exception
    {
        mockStatic(TransportProtocolUtil.class);

        ITransport transport = mock(ITransport.class);

        final Channel channel = mock(Channel.class);
        final ChannelFuture closeFuture = new DefaultChannelFuture(channel, false);

        when(channel.getCloseFuture()).thenReturn(closeFuture);
        when(channel.getAttachment()).thenReturn(new ChannelData(UserID.DUMMY, DID.generate()));
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

        verifyStatic();
        TransportProtocolUtil.sessionEnded(any(Endpoint.class), eq(outgoingEventSink), eq(streamManager), eq(true), eq(false));
    }

    @Test
    public void shouldCloseInboundPeerStreamsWhenChannelCloseIsCalled()
            throws Exception
    {
        mockStatic(TransportProtocolUtil.class);

        ITransport transport = mock(ITransport.class);

        final Channel channel = mock(Channel.class);
        final ChannelFuture closeFuture = new DefaultChannelFuture(channel, false);

        when(channel.getCloseFuture()).thenReturn(closeFuture);
        when(channel.getAttachment()).thenReturn(new ChannelData(UserID.DUMMY, DID.generate()));
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

        ChannelTeardownHandler handler = new ChannelTeardownHandler(transport, outgoingEventSink, streamManager, ChannelMode.SERVER);
        handler.channelOpen(ctx, new UpstreamChannelStateEvent(channel, ChannelState.OPEN, Boolean.TRUE));
        verify(ctx).sendUpstream(any(ChannelEvent.class));

        channel.close();

        verifyStatic();
        TransportProtocolUtil.sessionEnded(any(Endpoint.class), eq(outgoingEventSink), eq(streamManager), eq(false), eq(true));
    }

    @Test
    public void shouldCloseBothInboundAndOutboundPeerStreamsWhenChannelCloseIsCalled()
            throws Exception
    {
        mockStatic(TransportProtocolUtil.class);

        ITransport transport = mock(ITransport.class);

        final Channel channel = mock(Channel.class);
        final ChannelFuture closeFuture = new DefaultChannelFuture(channel, false);

        when(channel.getCloseFuture()).thenReturn(closeFuture);
        when(channel.getAttachment()).thenReturn(new ChannelData(UserID.DUMMY, DID.generate()));
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

        ChannelTeardownHandler handler = new ChannelTeardownHandler(transport, outgoingEventSink, streamManager, ChannelMode.TWOWAY);
        handler.channelOpen(ctx, new UpstreamChannelStateEvent(channel, ChannelState.OPEN, Boolean.TRUE));
        verify(ctx).sendUpstream(any(ChannelEvent.class));

        channel.close();

        verifyStatic();
        TransportProtocolUtil.sessionEnded(any(Endpoint.class), eq(outgoingEventSink), eq(streamManager), eq(true), eq(true));
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
