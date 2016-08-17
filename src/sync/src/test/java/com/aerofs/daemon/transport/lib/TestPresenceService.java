/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.testlib.LoggerSetup;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;

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
        presenceService = new PresenceService(null, mock(CoreQueue.class),
                MoreExecutors.sameThreadExecutor());
    }

    @Test
    public void shouldNotifyDeviceOnlineIfDeviceUnreachableOnMulticastAndBecomesConnectedOnUnicast()
        throws Exception
    {
        presenceService.addListener((did, isOnline) -> {
            assertThat(did, equalTo(DID_0));
            assertThat(isOnline, equalTo(true));
        });

        presenceService.onDeviceConnected(DID_0, UserID.DUMMY);

        assertThat(presenceService.isPotentiallyAvailable(DID_0), equalTo(true));
    }



    @Test
    public void shouldNotifyDeviceOfflineIfDeviceBecomesDisconnectedFromUnicastAndIsNotReachableOnMulticast()
            throws Exception
    {
        presenceService.onDeviceConnected(DID_0, UserID.DUMMY);

        presenceService.addListener((did, isOnline) -> {
            assertThat(did, equalTo(DID_0));
            assertThat(isOnline, equalTo(false));
        });

        presenceService.onDeviceDisconnected(DID_0);

        assertThat(presenceService.isPotentiallyAvailable(DID_0), equalTo(false));
    }

    @Test
    public void shouldNotifyDeviceOnlineOnlyWhenPresenceConvertsFromOfflineToOnline() // i.e. presence notifications are edge-triggered
            throws Exception
    {
        final AtomicInteger presenceNotifications = new AtomicInteger(0);
        presenceService.addListener((did, isPotentiallyAvailable) -> {
            assertThat(did, equalTo(DID_0));
            assertThat(isPotentiallyAvailable, equalTo(true));

            presenceNotifications.getAndIncrement();
        });

        // NOTE: some of the transport components _may_ report connection twice
        presenceService.onDeviceConnected(DID_0, UserID.DUMMY);
        presenceService.onDeviceConnected(DID_0, UserID.DUMMY);

        assertThat(presenceNotifications.get(), equalTo(1));
    }

    @Test
    public void shouldReturnCorrectSetOfOnlineDevices() {
        presenceService.onDeviceConnected(DID_0, UserID.DUMMY);
        presenceService.onDeviceConnected(DID_1, UserID.DUMMY);
        presenceService.onDeviceConnected(DID_2, UserID.DUMMY);
        presenceService.onDeviceConnected(DID_3, UserID.DUMMY);
        presenceService.onDeviceDisconnected(DID_1);

        assertThat("did0", presenceService.isPotentiallyAvailable(DID_0));
        assertThat("did1", !presenceService.isPotentiallyAvailable(DID_1));
        assertThat("did2", presenceService.isPotentiallyAvailable(DID_2));
        assertThat("did3", presenceService.isPotentiallyAvailable(DID_3));
    }
}
