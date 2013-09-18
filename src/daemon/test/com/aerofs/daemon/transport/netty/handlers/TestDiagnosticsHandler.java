/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.netty.handlers;

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

public class TestDiagnosticsHandler
{
    private RockLog _rockLog = mock(RockLog.class);
    private Timer _timer = mock(Timer.class);
    private ChannelHandlerContext _ctx = mock(ChannelHandlerContext.class);
    private ChannelStateEvent _e = mock(ChannelStateEvent.class);
    private Channel _channel = mock(Channel.class);
    private ChannelPipeline _pipeline = mock(ChannelPipeline.class);
    private Defect _defect = mock(Defect.class);
    private Timeout _timeout = mock(Timeout.class);

    private DiagnosticsHandler _diagnosticsHandler;
    private TimerTask _timerTask;

    @Before
    public void setup()
    {
        when(_ctx.getChannel()).thenReturn(_channel);
        when(_ctx.getPipeline()).thenReturn(_pipeline);

        when(_rockLog.newDefect(anyString())).thenReturn(_defect);
        when(_defect.setMessage(anyString())).thenReturn(_defect);
        when(_defect.addData(anyString(), anyObject())).thenReturn(_defect);

        when(_timer.newTimeout(any(TimerTask.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(
        new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                Object [] arguments = invocation.getArguments();
                _timerTask = (TimerTask) checkNotNull(arguments[0]);
                return _timeout;
            }
        });

        _diagnosticsHandler = new DiagnosticsHandler("z", _rockLog, _timer);
    }

    @Test
    public void shouldSendDefectIfChannelDoesntConnectInTime()
            throws Exception
    {
        when(_channel.isOpen()).thenReturn(true); // haven't disconnected yet

        _diagnosticsHandler.channelOpen(_ctx, _e); // created the channel

        assertTrue(_timerTask != null);
        _timerTask.run(_timeout); // timeout passed; channel still open, still connecting

        verify(_defect).send();
    }

    @Test
    public void shouldNotSendDefectIfChannelConnectsInTime()
            throws Exception
    {
        when(_channel.isOpen()).thenReturn(true); // haven't disconnected yet

        _diagnosticsHandler.channelOpen(_ctx, _e); // create the channel

        assertTrue(_timerTask != null);

        _diagnosticsHandler.channelConnected(_ctx, _e); // we've connected

        _timerTask.run(_timeout); // now the timeout triggers

        verifyNoMoreInteractions(_rockLog, _defect);
    }

    @Test
    public void shouldSendDefectIfChannelClosesWithoutConnecting()
            throws Exception
    {
        when(_channel.isOpen()).thenReturn(false); // by the time the timer triggers the channel is already closed

        _diagnosticsHandler.channelOpen(_ctx, _e); // create the channel

        assertTrue(_timerTask != null);

        _diagnosticsHandler.channelClosed(_ctx, _e); // channel closes before we connect

        _timerTask.run(_timeout); // now the timer runs

        verify(_defect).send(); // only one defect should be sent
    }
}
