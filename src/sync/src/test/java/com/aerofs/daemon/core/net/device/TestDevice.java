/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.device;

import com.aerofs.ids.DID;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDevice extends AbstractTest
{
    private final Device _dev = new Device(new DID(DID.generate()));

    private final ITransport _tp1 = mock(ITransport.class);
    private final ITransport _tp2 = mock(ITransport.class);
    private final ITransport _tp3 = mock(ITransport.class);

    @Before
    public void setUp()
            throws Exception
    {
        when(_tp1.rank()).thenReturn(1);
        when(_tp1.id()).thenReturn("tp1");

        when(_tp2.rank()).thenReturn(2);
        when(_tp2.id()).thenReturn("tp2");

        when(_tp3.rank()).thenReturn(3);
        when(_tp3.id()).thenReturn("tp3");
    }

    @Test
    public void shouldReturnBestTransportAsPreferredTransportIfBothAreOnline()
    {
        putTp1AndTp2Online();

        assertEquals("checking tp1 is the best", _tp1, _dev.getPreferredTransport_());
    }

    @Test
    public void shouldReturnBestTransportAsPreferredTransportAfterMultipleStateChanges()
    {
        putTp1AndTp2Online();

        _dev.offline_(_tp1);
        _dev.online_(_tp1);

        assertEquals("checking tp1 is the best", _tp1, _dev.getPreferredTransport_());
    }

    /**
     * put both transports online; assume this works
     */
    private void putTp1AndTp2Online()
    {
        _dev.online_(_tp1); // ignore return
        _dev.online_(_tp2); // ignore return
    }
}
