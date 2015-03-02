/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.C;
import com.aerofs.testlib.LoggerSetup;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.UpstreamChannelStateEvent;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class TestConnectTimeoutHandler
{
    static
    {
        LoggerSetup.init();
    }

    private ConnectTimeoutHandler connectHandler;
    private Channel channel;
    private ChannelHandlerContext ctx;
    private ChannelPipeline pipeline;
    private Timer timer;
    private Timeout timeout;

    @Before
    public void setup()
    {
        timer = mock(Timer.class);
        timeout = mock(Timeout.class);
        ctx = mock(ChannelHandlerContext.class);
        channel = mock(Channel.class);
        pipeline = mock(ChannelPipeline.class);

        when(ctx.getChannel()).thenReturn(channel);
        when(ctx.getPipeline()).thenReturn(pipeline);
        when(channel.getCloseFuture()).thenReturn(mock(ChannelFuture.class));
        when(channel.getPipeline()).thenReturn(mock(ChannelPipeline.class));

        connectHandler = new ConnectTimeoutHandler(3 * C.SEC, timer);
    }

    @Test
    public void shouldCloseChannelIfNotConnectedAfterTimeout() throws Exception
    {
        connectHandler.channelOpen(ctx, new UpstreamChannelStateEvent(channel, ChannelState.OPEN, Boolean.TRUE));

        ArgumentCaptor<TimerTask> argument = ArgumentCaptor.forClass(TimerTask.class);

        verify(timer).newTimeout(argument.capture(), anyLong(), any(TimeUnit.class));
        argument.getValue().run(mock(Timeout.class));

        // FIXME: I would love to verify the channel gets closed, but netty won't say. The only
        // reasonable call is a static (fireExceptionCaughtLater) which is not verifiable from here.
    }

    @Test
    public void shouldCancelFutureIfChannelConnects() throws Exception
    {
        doReturn(timeout).when(timer).newTimeout(any(TimerTask.class), anyLong(), any(TimeUnit.class));

        connectHandler.channelOpen(ctx, new UpstreamChannelStateEvent(channel, ChannelState.OPEN, Boolean.TRUE));
        connectHandler.channelConnected(ctx, new UpstreamChannelStateEvent(channel, ChannelState.CONNECTED, Boolean.TRUE));

        verify(timeout).cancel();
        verify(pipeline).remove(connectHandler);
    }

    @Test
    public void shouldCancelFutureIfChannelDisconnects() throws Exception
    {
        doReturn(timeout).when(timer).newTimeout(any(TimerTask.class), anyLong(), any(TimeUnit.class));

        connectHandler.channelOpen(ctx, new UpstreamChannelStateEvent(channel, ChannelState.OPEN, Boolean.TRUE));
        connectHandler.channelDisconnected(ctx, new UpstreamChannelStateEvent(channel, ChannelState.CONNECTED, Boolean.TRUE));

        verify(timeout).cancel();
        verify(pipeline).remove(connectHandler);
    }
}
