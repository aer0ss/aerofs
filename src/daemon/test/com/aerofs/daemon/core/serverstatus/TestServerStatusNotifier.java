/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.serverstatus;

import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.Server;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.ritual_notification.RitualNotifier;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static junit.framework.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

public class TestServerStatusNotifier
{
    @Mock ServerConnectionStatus _serverConnectionStatus;
    @Mock RitualNotifier _ritualNotifier;

    @InjectMocks ServerStatusNotifier _notifier;

    @Before
    public void setup()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldSubscribeToServerConnectionStatusOnStart()
    {
        _notifier.start();

        verify(_serverConnectionStatus).addListener(_notifier, Server.VERKEHR);
    }

    @Test
    public void shouldSendServerStatusNotification()
    {
        _notifier.sendNotification(true);

        // FIXME (AT): refactor Notifications so we can verify the notification we send is correct.
        verify(_ritualNotifier).sendNotification(any(PBNotification.class));
    }

    @Test
    public void shouldReturnVerkehrAvailability()
    {
        assertTrue(_notifier.isAvailable(ImmutableMap.of(Server.VERKEHR, true)));
        assertFalse(_notifier.isAvailable(ImmutableMap.of(Server.VERKEHR, false)));
    }

    @Test
    public void shouldNotifyOnServerAvailable()
    {
        _notifier.available();

        verify(_ritualNotifier).sendNotification(any(PBNotification.class));
    }

    @Test
    public void shouldNotifyOnServerUnavailable()
    {
        _notifier.unavailable();

        verify(_ritualNotifier).sendNotification(any(PBNotification.class));
    }
}
