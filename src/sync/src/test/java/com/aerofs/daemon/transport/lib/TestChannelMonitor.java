/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.ids.DID;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.*;

public class TestChannelMonitor extends AbstractTest
{
    DID did;
    ChannelDirectory directory;
    ChannelFuture lastFuture;
    IPresenceLocation loc = mock(IPresenceLocation.class);
    IUnicastConnector connector = mock(IUnicastConnector.class);
    PresenceLocations locations = new PresenceLocations();

    // one does not simply spy on a HashedWheelTimer directly
    // it used to work in the past but since netty 3.9.5 it causes calls to newTimeout to hang
    Timer timer = spy(new Timer() {
        Timer t = new HashedWheelTimer();

        @Override
        public Timeout newTimeout(TimerTask timerTask, long l, TimeUnit timeUnit)
        {
            return t.newTimeout(timerTask, l, timeUnit);
        }

        @Override
        public Set<Timeout> stop()
        {
            return t.stop();
        }
    });

    @Before
    public void setup() throws Exception
    {
        did = new DID("91200100000000000000000000000456");
        directory = spy(new ChannelDirectory(mock(ITransport.class), mock(IUnicastConnector.class)));

        doAnswer(invocation -> {
            lastFuture = new DefaultChannelFuture(mock(Channel.class), true);
            return lastFuture;
        }).when(directory).chooseActiveChannel(any(DID.class));

        when(connector.newChannel(any(DID.class), any(IPresenceLocation.class)))
                .thenReturn(mock(ChannelFuture.class));
    }

    @After
    public void tearDown()
    {
        timer.stop();
    }

    @Test
    public void shouldConnectIfPresent() throws Exception
    {
        ChannelMonitor alloc = new ChannelMonitor(connector, locations, directory, timer);
        ArgumentCaptor<TimerTask> timerTask = ArgumentCaptor.forClass(TimerTask.class);

        alloc.onPresenceReceived(did, ImmutableSet.of(loc));

        verify(connector, times(1)).newChannel(did, loc);

        alloc.onDevicePresenceChanged(did, false);
        // grab the timer task and call it now.
        verify(timer).newTimeout(timerTask.capture(), anyLong(), Matchers.<TimeUnit>any());
        timerTask.getValue().run(mock(Timeout.class));

        verify(directory).chooseActiveChannel(did);
        verify(timer).newTimeout(timerTask.capture(), anyLong(), Matchers.<TimeUnit>any());
    }

    @Test
    public void shouldNotConnectIfNotPresent() throws Exception
    {
        ChannelMonitor alloc = new ChannelMonitor(connector, locations, directory, timer);
        ArgumentCaptor<TimerTask> timerTask = ArgumentCaptor.forClass(TimerTask.class);

        alloc.onPresenceReceived(did, ImmutableSet.of(loc));
        verify(connector, times(1)).newChannel(did, loc);

        // Okay, initial attempt was ok; make the device not potentially available and frob the timer
        alloc.onPresenceReceived(did, ImmutableSet.of());

        alloc.onDevicePresenceChanged(did, false);
        // grab the timer task and call it now.
        verify(timer).newTimeout(timerTask.capture(), anyLong(), Matchers.<TimeUnit>any());
        timerTask.getValue().run(mock(Timeout.class));

        // doesn't retry chooseActiveFuture; device is not currently potentially available
        verify(directory, never()).chooseActiveChannel(did);
    }

    @Test
    public void shouldScheduleReconnectWhenGoingOffline() throws Exception
    {
        ChannelMonitor monitor = new ChannelMonitor(connector, locations, directory, timer);
        ArgumentCaptor<TimerTask> timerTask = ArgumentCaptor.forClass(TimerTask.class);

        monitor.onDevicePresenceChanged(did, false);
        verify(timer, times(1)).newTimeout(timerTask.capture(), anyLong(), Matchers.<TimeUnit>any());
        monitor.onDevicePresenceChanged(did, true);
    }

    @Test
    public void shouldReturnCorrectSet() throws Exception
    {
        DID did0 = new DID("91200100000000000000000000000100");
        DID did1 = new DID("a1200100000000000000000000000111");
        DID did2 = new DID("b1200100000000000000000000000222");
        DID did3 = new DID("c1200100000000000000000000000333");

        ChannelMonitor monitor = new ChannelMonitor(connector, locations, directory, timer);
        monitor.onPresenceReceived(did0, ImmutableSet.of(loc));
        monitor.onPresenceReceived(did1, ImmutableSet.of(loc));
        monitor.onPresenceReceived(did2, ImmutableSet.of(loc));
        monitor.onPresenceReceived(did3, ImmutableSet.of(loc));
        monitor.onPresenceReceived(did1, ImmutableSet.of());

        assertThat(monitor.allReachableDevices(), containsInAnyOrder(did0, did2, did3));
    }
}