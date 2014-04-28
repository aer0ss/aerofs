/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.testlib.AbstractTest;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.mockito.Mock;

import java.util.TreeSet;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestChannelDirectory extends AbstractTest
{
    @Mock ITransport tp;
    @Mock Channel channel;
    @Mock Channel otherChannel;
    @Mock ChannelFuture future;
    @Captor ArgumentCaptor<ChannelFutureListener> listener;

    @Mock IUnicastListener unicastListener;
    DID did;

    @Before
    public void setUp() throws Exception
    {
        did = new DID("99900100000000000000000000000456");
        when(channel.getId()).thenReturn(1);
        when(channel.getCloseFuture()).thenReturn(future);
        when(channel.compareTo(otherChannel)).thenReturn(-1);
        when(otherChannel.getId()).thenReturn(2);
        when(otherChannel.getCloseFuture()).thenReturn(future);
        when(otherChannel.compareTo(channel)).thenReturn(1);

        assertNotEquals(channel, otherChannel);
    }

    /* The simplest positive test case */
    @Test
    public void shouldCountChannels() throws Exception
    {
        ChannelDirectory channelDirectory = new ChannelDirectory(tp);
        channelDirectory.setUnicastListener(unicastListener);

        assertEquals(0, channelDirectory.getSnapshot(did).size());

        channelDirectory.register(channel, did);
        assertEquals(1, channelDirectory.getSnapshot(did).size());
        assertEquals(1, channelDirectory.getActiveDevices().size());

        for (DID d : channelDirectory.getActiveDevices()){
            assertEquals(did, d);
        }

        channelDirectory.register(otherChannel, did);
        assertEquals(1, channelDirectory.getActiveDevices().size());
        assertEquals(2, channelDirectory.getSnapshot(did).size());
        for (DID d : channelDirectory.getActiveDevices()){
            assertEquals(did, d);
        }
    }

    @Test
    public void shouldAutoRemove()
            throws Exception
    {
        ChannelDirectory channelDirectory = new ChannelDirectory(tp);
        channelDirectory.setUnicastListener(unicastListener);
        channelDirectory.register(channel, did);

        verify(future).addListener(listener.capture());
        listener.getValue().operationComplete(future);

        assertEquals(0, channelDirectory.getSnapshot(did).size());
    }
}