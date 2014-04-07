/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.testlib.LoggerSetup;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.collect.Sets.newHashSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
    public void shouldNotifyDeviceOnlineIfDeviceBecomesReachableOnMulticastAndNoUnicastConnectionExists()
            throws Exception
    {
        final Set<DID> didsAlreadyPresent = newHashSet();
        presenceService.addListener(new IDevicePresenceListener()
        {
            @Override
            public void onDevicePresenceChanged(DID did, boolean isOnline)
            {
                assertThat(isOnline, equalTo(true));
                assertThat(didsAlreadyPresent.add(did), equalTo(true));
            }
        });

        // notify the same DID multiple times
        presenceService.onDeviceReachable(DID_0);
        presenceService.onDeviceReachable(DID_0);
        // notify this DID only once
        presenceService.onDeviceReachable(DID_1);

        assertThat(didsAlreadyPresent, containsInAnyOrder(DID_0, DID_1));

        assertThat(presenceService.isPotentiallyAvailable(DID_0), equalTo(true));
        assertThat(presenceService.isPotentiallyAvailable(DID_1), equalTo(true));
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
    public void shouldNotNotifyDeviceOfflineIfMulticastServiceBecomesUnavailableAndUnicastConnectionExists()
            throws Exception
    {
        presenceService.onDeviceReachable(DID_0);
        presenceService.onDeviceConnected(DID_0);

        IDevicePresenceListener listener = mock(IDevicePresenceListener.class);
        presenceService.addListener(listener);

        presenceService.onMulticastUnavailable();
        verifyNoMoreInteractions(listener);

        assertThat(presenceService.isPotentiallyAvailable(DID_0), equalTo(true));
    }

    @Test
    public void shouldNotNotifyDeviceOfflineIfDeviceBecomesUnreachableOnMulticastAndUnicastConnectionExists()
            throws Exception
    {
        presenceService.onDeviceReachable(DID_0);
        presenceService.onDeviceConnected(DID_0);

        IDevicePresenceListener listener = mock(IDevicePresenceListener.class);
        presenceService.addListener(listener);

        presenceService.onDeviceUnreachable(DID_0);
        verifyNoMoreInteractions(listener);

        assertThat(presenceService.isPotentiallyAvailable(DID_0), equalTo(true));
    }

    @Test
    public void shouldNotifyDeviceOfflineIfMulticastBecomesUnavailableAndNoUnicastConnectionExists()
            throws Exception
    {
        presenceService.onDeviceReachable(DID_0);

        presenceService.addListener(new IDevicePresenceListener()
        {
            @Override
            public void onDevicePresenceChanged(DID did, boolean isOnline)
            {
                assertThat(did, equalTo(DID_0));
                assertThat(isOnline, equalTo(false));
            }
        });

        presenceService.onMulticastUnavailable();

        assertThat(presenceService.isPotentiallyAvailable(DID_0), equalTo(false));
    }

    @Test
    public void shouldNotifyDeviceOfflineIfDeviceBecomesUnreachableOnMulticastAndNoUnicastConnectionExists()
            throws Exception
    {
        presenceService.onDeviceReachable(DID_0);

        presenceService.addListener(new IDevicePresenceListener()
        {
            @Override
            public void onDevicePresenceChanged(DID did, boolean isOnline)
            {
                assertThat(did, equalTo(DID_0));
                assertThat(isOnline, equalTo(false));
            }
        });

        presenceService.onDeviceUnreachable(DID_0);

        assertThat(presenceService.isPotentiallyAvailable(DID_0), equalTo(false));
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
    public void shouldNotNotifyDeviceOfflineIfDeviceBecomesDisconnectedFromUnicastAndIsReachableOnMulticast()
            throws Exception
    {
        presenceService.onDeviceReachable(DID_0);
        presenceService.onDeviceConnected(DID_0);

        IDevicePresenceListener listener = mock(IDevicePresenceListener.class);
        presenceService.addListener(listener);
        verifyNoMoreInteractions(listener);

        presenceService.onDeviceDisconnected(DID_0);

        assertThat(presenceService.isPotentiallyAvailable(DID_0), equalTo(true));
    }

    @Test
    public void shouldNotNotifyDeviceOnlineIfDeviceBecomesReachableOnMulticastAndUnicastConnectionExists()
            throws Exception
    {
        presenceService.onDeviceConnected(DID_0);

        IDevicePresenceListener listener = mock(IDevicePresenceListener.class);
        presenceService.addListener(listener);
        verifyNoMoreInteractions(listener);

        presenceService.onDeviceReachable(DID_0);

        assertThat(presenceService.isPotentiallyAvailable(DID_0), equalTo(true));
    }

    @Test
    public void shouldNotNotifyDeviceOnlineIfDeviceConnectsOnUnicastAndIsAlreadyReachableOnMulticast()
            throws Exception
    {
        presenceService.onDeviceReachable(DID_0);

        IDevicePresenceListener listener = mock(IDevicePresenceListener.class);
        presenceService.addListener(listener);
        verifyNoMoreInteractions(listener);

        presenceService.onDeviceConnected(DID_0);

        assertThat(presenceService.isPotentiallyAvailable(DID_0), equalTo(true));
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

        presenceService.onDeviceReachable(DID_0);
        // NOTE: some of the transport components _may_ report connection twice
        presenceService.onDeviceConnected(DID_0);
        presenceService.onDeviceConnected(DID_0);

        assertThat(presenceNotifications.get(), equalTo(1));
    }

    @Test
    public void shouldOnlyReportDevicesWithoutBothUnicastAndMulticastAsOfflineWhenMulticastServiceBecomesUnavailable()
            throws Exception
    {
        presenceService.onDeviceConnected(DID_0);
        presenceService.onDeviceReachable(DID_1);
        presenceService.onDeviceConnected(DID_1);
        presenceService.onDeviceReachable(DID_2);

        IDevicePresenceListener presenceListener = mock(IDevicePresenceListener.class);
        presenceService.addListener(presenceListener);

        presenceService.onMulticastUnavailable();

        verify(presenceListener).onDevicePresenceChanged(DID_2, false);
        verifyNoMoreInteractions(presenceListener);
    }

    @Test
    public void shouldReturnCorrectSetOfOnlineDevices() {
        presenceService.onDeviceReachable(DID_0);
        presenceService.onDeviceConnected(DID_0);
        presenceService.onDeviceConnected(DID_1);
        presenceService.onDeviceConnected(DID_2);
        presenceService.onDeviceConnected(DID_3);
        presenceService.onDeviceUnreachable(DID_2);
        presenceService.onDeviceDisconnected(DID_1);

        assertThat(presenceService.allPotentiallyAvailable(), containsInAnyOrder(DID_0, DID_2, DID_3));
    }
}
