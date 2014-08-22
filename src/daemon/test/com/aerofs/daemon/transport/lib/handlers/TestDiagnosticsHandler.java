/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.defects.AutoDefect;
import com.aerofs.defects.Defect;
import com.aerofs.defects.DefectFactory;
import com.aerofs.defects.MockDefects;
import com.aerofs.testlib.LoggerSetup;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public final class TestDiagnosticsHandler
{
    static
    {
        LoggerSetup.init();
    }

    private Timer timer = mock(Timer.class);
    private ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
    private ChannelStateEvent e = mock(ChannelStateEvent.class);
    private Channel channel = mock(Channel.class);
    private ChannelPipeline pipeline = mock(ChannelPipeline.class);
    private Timeout timeout = mock(Timeout.class);

    private ConnectionStatsHandler connectionStatsHandler;
    private TimerTask timerTask;

    private DefectFactory defectFactory;
    private AutoDefect defect;

    @Before
    public void initMocks()
    {
        defectFactory = mock(DefectFactory.class);
        defect = mock(AutoDefect.class);
        MockDefects.init(defectFactory, defect);
    }

    @Before
    public void setup()
    {
        when(ctx.getChannel()).thenReturn(channel);
        when(ctx.getPipeline()).thenReturn(pipeline);

        when(timer.newTimeout(any(TimerTask.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Object[] arguments = invocation.getArguments();
                timerTask = (TimerTask)checkNotNull(arguments[0]);
                return timeout;
            }
        });

        connectionStatsHandler = new ConnectionStatsHandler("z", timer);
    }

    @Test
    public void shouldSendDefectIfChannelDoesntConnectInTime()
            throws Exception
    {
        when(channel.isOpen()).thenReturn(true); // haven't disconnected yet

        connectionStatsHandler.channelOpen(ctx, e); // created the channel

        assertTrue(timerTask != null);
        timerTask.run(timeout); // timeout passed; channel still open, still connecting

        verify(defect).sendAsync();
    }

    @Test
    public void shouldNotSendDefectIfChannelConnectsInTime()
            throws Exception
    {
        when(channel.isOpen()).thenReturn(true); // haven't disconnected yet

        connectionStatsHandler.channelOpen(ctx, e); // create the channel

        assertTrue(timerTask != null);

        connectionStatsHandler.channelConnected(ctx, e); // we've connected

        timerTask.run(timeout); // now the timeout triggers

        verifyNoMoreInteractions(defectFactory, defect);
    }

    @Test
    public void shouldSendDefectIfChannelClosesWithoutConnecting()
            throws Exception
    {
        when(channel.isOpen()).thenReturn(false); // by the time the timer triggers the channel is already closed

        connectionStatsHandler.channelOpen(ctx, e); // create the channel

        assertTrue(timerTask != null);

        connectionStatsHandler.channelClosed(ctx, e); // channel closes before we connect

        timerTask.run(timeout); // now the timer runs

        verify(defect).sendAsync(); // only one defect should be sent
    }
}
