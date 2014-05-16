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

import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class TestChannelPreallocator extends AbstractTest
{
    DID did;
    ChannelDirectory directory;
    IDevicePresenceService presence;
    ChannelFuture lastFuture;
    Timer timer;

    @Before
    public void setup() throws Exception
    {
        did = new DID("91200100000000000000000000000456");
        directory = spy(new ChannelDirectory(mock(ITransport.class), mock(IUnicastConnector.class)));
        presence = mock(IDevicePresenceService.class);
        timer = spy(TimerUtil.getGlobalTimer());

        doAnswer(new Answer()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                lastFuture = new DefaultChannelFuture(mock(Channel.class), true);
                return lastFuture;
            }
        }).when(directory).chooseActiveChannel(did);
    }

    @Test
    public void shouldConnectIfPresent() throws Exception
    {
        ChannelPreallocator alloc = new ChannelPreallocator(presence, directory, timer);
        ArgumentCaptor<TimerTask> timerTask = ArgumentCaptor.forClass(TimerTask.class);

        doReturn(true).when(presence).isPotentiallyAvailable(did);

        alloc.onDevicePresenceChanged(did, true);

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
        ChannelPreallocator alloc = new ChannelPreallocator(presence, directory, timer);
        ArgumentCaptor<TimerTask> timerTask = ArgumentCaptor.forClass(TimerTask.class);

        doReturn(true).when(presence).isPotentiallyAvailable(did);

        alloc.onDevicePresenceChanged(did, true);
        verify(directory, times(1)).chooseActiveChannel(did);

        // Okay, initial attempt was ok; make the device not potentially available and frob the timer
        doReturn(false).when(presence).isPotentiallyAvailable(did);

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
        ChannelPreallocator alloc = new ChannelPreallocator(presence, directory, timer);
        ArgumentCaptor<TimerTask> timerTask = ArgumentCaptor.forClass(TimerTask.class);
        doReturn(true).when(presence).isPotentiallyAvailable(did);

        alloc.onDevicePresenceChanged(did, true);
        verify(directory, times(1)).chooseActiveChannel(did);

        // finishing the future causes a reschedule; grab the timer task and call it now.
        lastFuture.setSuccess();
        verifyNoMoreInteractions(timer);
        verifyNoMoreInteractions(directory);
    }
}