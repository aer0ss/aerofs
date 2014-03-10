/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.TransportLoggerSetup;
import com.aerofs.lib.event.Prio;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class TestUnicastProxy
{
    static
    {
        TransportLoggerSetup.init();
    }

    private final IUnicast unicast = mock(IUnicast.class);
    private final Object cookie = new Object();

    @Test
    public void shouldReturnCorrectObjectFromUnderlyingUnicastWhenSendIsCalled()
            throws Exception
    {
        UnicastProxy proxy = new UnicastProxy();
        proxy.setRealUnicast(unicast);

        when(unicast.send(any(DID.class), any(IResultWaiter.class), any(Prio.class), any(byte[][].class), anyObject())).thenReturn(cookie);

        DID did = DID.generate();
        IResultWaiter waiter = mock(IResultWaiter.class);
        Prio prio = Prio.LO;
        byte[][] wireChunks = new byte[][]{new byte[]{0}};

        Object returnedCookie = proxy.send(did, waiter, prio, wireChunks, null);

        verify(unicast).send(did, waiter, prio, wireChunks, null);
        assertThat(returnedCookie, equalTo(cookie));
    }

    @Test
    public void shouldThrowExceptionWhenUnderlyingUnicastThrowsException()
            throws Exception
    {
        UnicastProxy proxy = new UnicastProxy();
        proxy.setRealUnicast(unicast);

        ExDeviceUnavailable unavailable = new ExDeviceUnavailable("SOMEONE");
        when(unicast.send(any(DID.class), any(IResultWaiter.class), any(Prio.class), any(byte[][].class), anyObject())).thenThrow(unavailable);

        DID did = DID.generate();
        IResultWaiter waiter = mock(IResultWaiter.class);
        Prio prio = Prio.LO;
        byte[][] wireChunks = new byte[][]{new byte[]{0}};

        try {
            proxy.send(did, waiter, prio, wireChunks, null);
        } catch (ExDeviceUnavailable e) {
            assertThat(e, sameInstance(unavailable));
        }

        verify(unicast).send(did, waiter, prio, wireChunks, null);
    }
}
