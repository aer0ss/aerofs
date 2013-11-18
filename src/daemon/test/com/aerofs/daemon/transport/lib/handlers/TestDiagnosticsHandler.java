/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.daemon.transport.TransportLoggerSetup;
import com.aerofs.rocklog.Defect;
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public final class TestDiagnosticsHandler
{
    static
    {
        TransportLoggerSetup.init();
    }

    private RockLog rockLog = mock(RockLog.class);
    private Timer timer = mock(Timer.class);
    private ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
    private ChannelStateEvent e = mock(ChannelStateEvent.class);
    private Channel channel = mock(Channel.class);
    private ChannelPipeline pipeline = mock(ChannelPipeline.class);
    private Defect defect = mock(Defect.class);
    private Timeout timeout = mock(Timeout.class);

    private DiagnosticsHandler diagnosticsHandler;
    private TimerTask timerTask;

    @Before
    public void setup()
    {
        when(ctx.getChannel()).thenReturn(channel);
        when(ctx.getPipeline()).thenReturn(pipeline);

        when(rockLog.newDefect(anyString())).thenReturn(defect);
        when(defect.setMessage(anyString())).thenReturn(defect);
        when(defect.addData(anyString(), anyObject())).thenReturn(defect);

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

        diagnosticsHandler = new DiagnosticsHandler("z", rockLog, timer);
    }

    @Test
    public void shouldSendDefectIfChannelDoesntConnectInTime()
            throws Exception
    {
        when(channel.isOpen()).thenReturn(true); // haven't disconnected yet

        diagnosticsHandler.channelOpen(ctx, e); // created the channel

        assertTrue(timerTask != null);
        timerTask.run(timeout); // timeout passed; channel still open, still connecting

        verify(defect).send();
    }

    @Test
    public void shouldNotSendDefectIfChannelConnectsInTime()
            throws Exception
    {
        when(channel.isOpen()).thenReturn(true); // haven't disconnected yet

        diagnosticsHandler.channelOpen(ctx, e); // create the channel

        assertTrue(timerTask != null);

        diagnosticsHandler.channelConnected(ctx, e); // we've connected

        timerTask.run(timeout); // now the timeout triggers

        verifyNoMoreInteractions(rockLog, defect);
    }

    @Test
    public void shouldSendDefectIfChannelClosesWithoutConnecting()
            throws Exception
    {
        when(channel.isOpen()).thenReturn(false); // by the time the timer triggers the channel is already closed

        diagnosticsHandler.channelOpen(ctx, e); // create the channel

        assertTrue(timerTask != null);

        diagnosticsHandler.channelClosed(ctx, e); // channel closes before we connect

        timerTask.run(timeout); // now the timer runs

        verify(defect).send(); // only one defect should be sent
    }
}
