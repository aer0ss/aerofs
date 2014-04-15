/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.rocklog.RockLog;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class TestTransportRoutingLayer
{
    private final DID localdid = DID.generate();
    private final CfgLocalDID cfgLocalDID = mock(CfgLocalDID.class);
    private final CoreQueue coreQueue = mock(CoreQueue.class);
    private final DevicePresence devicePresence = mock(DevicePresence.class);
    private final Transports transports = mock(Transports.class);
    private final UnicastInputOutputStack stack = mock(UnicastInputOutputStack.class);
    private final IUnicastOutputLayer outputLayer = mock(IUnicastOutputLayer.class);
    private final RockLog rockLog = mock(RockLog.class);
    private final Device device = mock(Device.class);

    private TransportRoutingLayer trl = new TransportRoutingLayer();

    @Before
    public void setup()
    {
        trl.inject_(cfgLocalDID, coreQueue, devicePresence, transports, stack, rockLog);

        when(cfgLocalDID.get()).thenReturn(localdid);

        when(stack.output()).thenReturn(outputLayer);
    }

    @Test
    public void shouldStartPulseWhenPacketFailsToBeSent()
            throws Exception
    {
        final DID remotedid = DID.generate();

        ITransport preferred = mock(ITransport.class);
        when(device.getPreferredTransport_()).thenReturn(preferred);
        when(devicePresence.getOPMDevice_(remotedid)).thenReturn(device);

        // send the packet
        trl.sendUnicast_(remotedid, PBCore.getDefaultInstance());

        ArgumentCaptor<IResultWaiter> callbackCaptor = ArgumentCaptor.forClass(IResultWaiter.class);
        verify(outputLayer).sendUnicastDatagram_(any(byte[].class), callbackCaptor.capture(), argThat(isEndpoint(remotedid, preferred)));

        // pretend the send failed
        IResultWaiter waiter = callbackCaptor.getValue();
        waiter.error(new IllegalArgumentException("something broke"));

        // verify that we enqueue
        ArgumentCaptor<IEvent> eventCaptor = ArgumentCaptor.forClass(IEvent.class);
        verify(coreQueue).enqueueBlocking(eventCaptor.capture(), eq(Prio.LO));

        // now, run the enqueued event
        // it should start pulsing
        AbstractEBSelfHandling enqued = (AbstractEBSelfHandling) eventCaptor.getValue();
        enqued.handle_();
        verify(devicePresence).startPulse_(preferred, remotedid);
    }

    private Matcher<Endpoint> isEndpoint(final DID remotedid, final ITransport preferred)
    {
        return new TypeSafeDiagnosingMatcher<Endpoint>()
        {
            @Override
            protected boolean matchesSafely(Endpoint ep, Description description)
            {
                return remotedid.equals(ep.did()) && preferred.equals(ep.tp());
            }

            @Override
            public void describeTo(Description description)
            {
                // noop?
            }
        };
    }
}
