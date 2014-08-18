/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.TimerUtil;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.testlib.AbstractTest;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class TestChannelMonitor extends AbstractTest
{
    DID did;
    ChannelDirectory directory;
    ChannelFuture lastFuture;
    Timer timer;

    @Before
    public void setup() throws Exception
    {
        did = new DID("91200100000000000000000000000456");
        directory = spy(new ChannelDirectory(mock(ITransport.class), mock(IUnicastConnector.class)));
        timer = spy(TimerUtil.getGlobalTimer());

        doAnswer(new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                lastFuture = new DefaultChannelFuture(mock(Channel.class), true);
                return lastFuture;
            }
        }).when(directory).chooseActiveChannel(any(DID.class));
    }

    @Test
    public void shouldConnectIfPresent() throws Exception
    {
        ChannelMonitor alloc = new ChannelMonitor(directory, timer);
        ArgumentCaptor<TimerTask> timerTask = ArgumentCaptor.forClass(TimerTask.class);

        alloc.onDeviceReachable(did);

        verify(directory, times(1)).chooseActiveChannel(did);

        lastFuture.setFailure(mock(Exception.class));
        // finishing the future causes a reschedule; grab the timer task and call it now.
        verify(timer).newTimeout(timerTask.capture(), anyLong(), Matchers.<TimeUnit>any());
        timerTask.getValue().run(mock(Timeout.class));

        verify(directory, times(2)).chooseActiveChannel(did);
        verify(timer).newTimeout(timerTask.capture(), anyLong(), Matchers.<TimeUnit>any());
    }

    @Test
    public void shouldNotConnectIfNotPresent() throws Exception
    {
        ChannelMonitor alloc = new ChannelMonitor(directory, timer);
        ArgumentCaptor<TimerTask> timerTask = ArgumentCaptor.forClass(TimerTask.class);

        alloc.onDeviceReachable(did);
        verify(directory, times(1)).chooseActiveChannel(did);

        // Okay, initial attempt was ok; make the device not potentially available and frob the timer
        alloc.onDeviceUnreachable(did);

        // finishing the future causes a reschedule; grab the timer task and call it now.
        lastFuture.setFailure(mock(Exception.class));
        verify(timer).newTimeout(timerTask.capture(), anyLong(), Matchers.<TimeUnit>any());
        timerTask.getValue().run(mock(Timeout.class));

        // doesn't retry chooseActiveFuture; device is not currently potentially available
        verify(directory, times(1)).chooseActiveChannel(did);
    }

    @Test
    public void shouldNotRetryAfterSuccess() throws Exception
    {
        ChannelMonitor monitor = new ChannelMonitor(directory, timer);

        monitor.onDeviceReachable(did);
        verify(directory, times(1)).chooseActiveChannel(did);

        // finishing the future causes a reschedule; grab the timer task and call it now.
        lastFuture.setSuccess();
        verifyNoMoreInteractions(timer);
        verifyNoMoreInteractions(directory);
    }

    @Test
    public void shouldScheduleReconnectWhenGoingOffline() throws Exception
    {
        ChannelMonitor monitor = new ChannelMonitor(directory, timer);
        ArgumentCaptor<TimerTask> timerTask = ArgumentCaptor.forClass(TimerTask.class);

        monitor.onDevicePresenceChanged(did, false);
        verify(timer, times(1)).newTimeout(timerTask.capture(), anyLong(), Matchers.<TimeUnit>any());
        monitor.onDevicePresenceChanged(did, true);
    }

    @Test
    public void shouldReturnCorrectSet() throws Exception
    {
        DID did0 = new DID("91200100000000000000000000000100");
        DID did1 = new DID("91200100000000000000000000000111");
        DID did2 = new DID("91200100000000000000000000000222");
        DID did3 = new DID("91200100000000000000000000000333");

        ChannelMonitor monitor = new ChannelMonitor(directory, timer);
        monitor.onDeviceReachable(did0);
        monitor.onDeviceReachable(did1);
        monitor.onDeviceReachable(did2);
        monitor.onDeviceReachable(did3);
        monitor.onDeviceUnreachable(did1);

        assertThat(monitor.allReachableDevices(), containsInAnyOrder(did0, did2, did3));
    }
}