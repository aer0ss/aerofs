/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.testlib.LoggerSetup;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public final class TestPresenceService
{
    static
    {
        LoggerSetup.init();
    }

    private static final DID DID_0 = DID.generate();
    private static final DID DID_1 = DID.generate();
    private static final DID DID_2 = DID.generate();
    private static final DID DID_3 = DID.generate();

    // Test cases defined using:
    // repos/aerofs/docs/design/transport/transport_presence_service.md
    //
    //   Multicast  |    Unicast    |   Result
    // --------------------------------------------------------------------------------------------------------
    //     ⌜       |   Connected   |  isPotentiallyAvailable = true ; no presence notification
    //     ⌜       | Disconnected  |  isPotentiallyAvailable = true ; 'potentially available' presence notification
    //     ⌝       |  Connected    |  isPotentiallyAvailable = true ; no presence notification
    //     ⌝       | Disconnected  |  isPotentiallyAvailable = false; 'unavailable' presence notification
    //
    // NOTE: for the multicast becoming 'unreachable' state we have to check _two_ cases:
    //   1. When the entire multicast service becomes unavailable
    //   2. When only the device becomes unavailable
    //
    //   Multicast  |    Unicast    |   Result
    // --------------------------------------------------------------------------------------------------------
    //   Reachable  |       ⌜       |  isPotentiallyAvailable = true ; no presence notification
    //  Unreachable |       ⌜       |  isPotentiallyAvailable = true ; 'potentially available' presence notification
    //   Reachable  |       ⌝       |  isPotentiallyAvailable = true; no presence notification
    //  Unreachable |       ⌝       |  isPotentiallyAvailable = false; 'unavailable' presence notification
    //

    private PresenceService presenceService;

    @Before
    public void setup()
    {
        presenceService = new PresenceService();
    }

    @Test
    public void shouldNotifyDeviceOnlineIfDeviceUnreachableOnMulticastAndBecomesConnectedOnUnicast()
        throws Exception
    {
        presenceService.addListener(new IDevicePresenceListener()
        {
            @Override
            public void onDevicePresenceChanged(DID did, boolean isOnline)
            {
                assertThat(did, equalTo(DID_0));
                assertThat(isOnline, equalTo(true));
            }
        });

        presenceService.onDeviceConnected(DID_0);

        assertThat(presenceService.isPotentiallyAvailable(DID_0), equalTo(true));
    }



    @Test
    public void shouldNotifyDeviceOfflineIfDeviceBecomesDisconnectedFromUnicastAndIsNotReachableOnMulticast()
            throws Exception
    {
        presenceService.onDeviceConnected(DID_0);

        presenceService.addListener(new IDevicePresenceListener()
        {
            @Override
            public void onDevicePresenceChanged(DID did, boolean isOnline)
            {
                assertThat(did, equalTo(DID_0));
                assertThat(isOnline, equalTo(false));
            }
        });

        presenceService.onDeviceDisconnected(DID_0);

        assertThat(presenceService.isPotentiallyAvailable(DID_0), equalTo(false));
    }

    @Test
    public void shouldNotifyDeviceOnlineOnlyWhenPresenceConvertsFromOfflineToOnline() // i.e. presence notifications are edge-triggered
            throws Exception
    {
        final AtomicInteger presenceNotifications = new AtomicInteger(0);
        presenceService.addListener(new IDevicePresenceListener()
        {
            @Override
            public void onDevicePresenceChanged(DID did, boolean isPotentiallyAvailable)
            {
                assertThat(did, equalTo(DID_0));
                assertThat(isPotentiallyAvailable, equalTo(true));

                presenceNotifications.getAndIncrement();
            }
        });

        // NOTE: some of the transport components _may_ report connection twice
        presenceService.onDeviceConnected(DID_0);
        presenceService.onDeviceConnected(DID_0);

        assertThat(presenceNotifications.get(), equalTo(1));
    }

    @Test
    public void shouldReturnCorrectSetOfOnlineDevices() {
        presenceService.onDeviceConnected(DID_0);
        presenceService.onDeviceConnected(DID_1);
        presenceService.onDeviceConnected(DID_2);
        presenceService.onDeviceConnected(DID_3);
        presenceService.onDeviceDisconnected(DID_1);

        assertThat("did0", presenceService.isPotentiallyAvailable(DID_0));
        assertThat("did1", !presenceService.isPotentiallyAvailable(DID_1));
        assertThat("did2", presenceService.isPotentiallyAvailable(DID_2));
        assertThat("did3", presenceService.isPotentiallyAvailable(DID_3));
    }
}
