/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.exceptions.ExTransportUnavailable;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.proto.Diagnostics.ChannelState;
import com.aerofs.testlib.AbstractTest;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestChannelDirectory extends AbstractTest
{
    @Mock ITransport tp;
    @Mock ChannelFuture future;
    @Captor ArgumentCaptor<ChannelFutureListener> listener;

    @Mock IDeviceConnectionListener deviceConnectionListener;
    DID did;

    public TestChannelDirectory()
            throws ExInvalidID
    {
        did = new DID("99900100000000000000000000000456");
    }


    private ChannelData getConnectedChannelData()
    {
        return new ChannelData(UserID.DUMMY, DID.generate());
    }

    private Channel[] getMockChannels(int count, ChannelState state)
    {
        List<Channel> list = new ArrayList<Channel>();

        for (int i=0; i<count; i++) {
            Channel c = mock(Channel.class);
            when(c.getId()).thenReturn(i);
            when(c.getCloseFuture()).thenReturn(future);

            if (state.equals(ChannelState.VERIFIED)) {
                ChannelData data = getConnectedChannelData(); // best variable name evar
                when(c.getAttachment()).thenReturn(data);
            }

            when(c.compareTo(any(Channel.class))).thenAnswer(new Answer<Integer>()
            {
                @Override
                public Integer answer(InvocationOnMock invocation)
                        throws Throwable
                {
                    return ((Channel)invocation.getArguments()[0]).getId() -
                            ((Channel)invocation.getMock()).getId();
                }
            });
            list.add(c);
        }
        return list.toArray(new Channel[0]);
    }

    /* The simplest positive test case */
    @Test
    public void shouldCountChannels() throws Exception
    {
        ChannelDirectory channelDirectory = new ChannelDirectory(tp, mock(IUnicastConnector.class));
        Channel[] channels = getMockChannels(2, ChannelState.CONNECTING);
        channelDirectory.setDeviceConnectionListener(deviceConnectionListener);

        assertEquals(0, channelDirectory.getSnapshot(did).size());

        channelDirectory.register(channels[0], did);
        assertEquals(1, channelDirectory.getSnapshot(did).size());
        assertEquals(1, channelDirectory.getActiveDevices().size());

        for (DID d : channelDirectory.getActiveDevices()){
            assertEquals(did, d);
        }

        channelDirectory.register(channels[1], did);
        assertEquals(1, channelDirectory.getActiveDevices().size());
        assertEquals(2, channelDirectory.getSnapshot(did).size());
        for (DID d : channelDirectory.getActiveDevices()){
            assertEquals(did, d);
        }
    }

    @Test
    public void shouldChooseActiveChannel() throws Exception
    {
        ChannelDirectory channelDirectory = new ChannelDirectory(tp, new IUnicastConnector() {
            @Override
            public ChannelFuture newChannel(DID did)
                    throws ExTransportUnavailable, ExDeviceUnavailable
            {
                return mock(ChannelFuture.class);
            }
        });
        channelDirectory.setDeviceConnectionListener(deviceConnectionListener);

        assertTrue(channelDirectory.getSnapshot(did).isEmpty());

        channelDirectory.register(getMockChannels(1, ChannelState.CONNECTING)[0], did);

        assertNotNull(channelDirectory.chooseActiveChannel(did).getChannel());
    }

    @Test
    public void shouldPreferActiveChannel() throws Exception
    {
        ChannelDirectory channelDirectory = new ChannelDirectory(tp, new IUnicastConnector() {
            @Override
            public ChannelFuture newChannel(DID did)
                    throws ExTransportUnavailable, ExDeviceUnavailable
            {
                return mock(ChannelFuture.class);
            }
        });
        channelDirectory.setDeviceConnectionListener(deviceConnectionListener);
        assertTrue(channelDirectory.getSnapshot(did).isEmpty());

        Channel connectingChannel = getMockChannels(1, ChannelState.CONNECTING)[0];
        Channel verifiedChannel = getMockChannels(2, ChannelState.VERIFIED)[1];
        assertNotEquals(connectingChannel, verifiedChannel);

        channelDirectory.register(connectingChannel, did);
        assertEquals(connectingChannel, channelDirectory.chooseActiveChannel(did).getChannel());

        channelDirectory.register(verifiedChannel, did);

        for (int i = 0; i < 10; i++) {
            Channel result = channelDirectory.chooseActiveChannel(did).getChannel();
            assertEquals(ChannelState.VERIFIED, TransportUtil.getChannelState(result));
            assertEquals(verifiedChannel, result);
        }
    }

    @Test
    public void shouldAutoRemove()
            throws Exception
    {
        ChannelDirectory channelDirectory = new ChannelDirectory(tp, mock(IUnicastConnector.class));
        channelDirectory.setDeviceConnectionListener(deviceConnectionListener);
        channelDirectory.register(getMockChannels(1, ChannelState.CONNECTING)[0], did);

        verify(future).addListener(listener.capture());
        listener.getValue().operationComplete(future);

        assertEquals(0, channelDirectory.getSnapshot(did).size());
    }
}