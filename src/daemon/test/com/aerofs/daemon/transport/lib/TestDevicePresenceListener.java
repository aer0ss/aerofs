/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.testlib.LoggerSetup;
import com.aerofs.rocklog.Defect;
import com.aerofs.rocklog.RockLog;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class TestDevicePresenceListener
{
    static
    {
        LoggerSetup.init();
    }

    private static final DID DID_0 = DID.generate();
    private static final String TRANSPORT_ID = "t";

    private final IUnicastInternal unicast = mock(IUnicastInternal.class);
    private final PulseManager pulseManager = mock(PulseManager.class);
    private final RockLog rockLog = mock(RockLog.class);
    private final Defect defect = mock(Defect.class);

    private DevicePresenceListener devicePresenceListener;

    @Before
    public void setup()
    {
        when(rockLog.newDefect(anyString())).thenReturn(defect);
        when(defect.addData(anyString(), anyString())).thenReturn(defect);
        devicePresenceListener = new DevicePresenceListener(TRANSPORT_ID, unicast, pulseManager, rockLog);
    }

    @Test
    public void shouldDisconnectAllUnicastConnectionsAndStopPulsingIfDeviceBecomesUnavailable()
            throws Exception
    {
        devicePresenceListener.onDevicePresenceChanged(DID_0, false);
        verify(unicast).disconnect(eq(DID_0), any(ExDeviceUnavailable.class));
        verify(pulseManager).stopPulse(DID_0, false);
    }

    @Test
    public void shouldStopPulsingIfDeviceBecomesPotentiallyAvailable()
            throws Exception
    {
        devicePresenceListener.onDevicePresenceChanged(DID_0, true);
        verify(pulseManager).stopPulse(DID_0, false);
    }

    @Test
    public void shouldStopPulsingAndSendARockLogDefectIfDeviceBecomesPotentiallyAvailableAndAPulseWasAlreadyOngoing()
            throws Exception
    {
        when(pulseManager.stopPulse(eq(DID_0), anyBoolean())).thenReturn(true);

        devicePresenceListener.onDevicePresenceChanged(DID_0, true);

        verify(pulseManager).stopPulse(DID_0, false);
        verify(rockLog).newDefect(anyString());
        verify(defect).send();
    }
}
