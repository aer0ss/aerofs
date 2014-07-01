/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.testlib.LoggerSetup;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public final class TestDevicePresenceListener
{
    static { LoggerSetup.init(); }

    private static final DID DID_0 = DID.generate();

    private final IUnicastInternal unicast = mock(IUnicastInternal.class);

    private DevicePresenceListener devicePresenceListener;

    @Before
    public void setup()
    {
        devicePresenceListener = new DevicePresenceListener(unicast);
    }

    @Test
    public void shouldDisconnectAllUnicastConnectionsIfDeviceBecomesUnavailable()
            throws Exception
    {
        devicePresenceListener.onDevicePresenceChanged(DID_0, false);
        verify(unicast).disconnect(eq(DID_0), any(ExDeviceUnavailable.class));
    }
}
