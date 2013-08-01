/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual_notification;

import com.aerofs.lib.AppRoot;
import com.aerofs.lib.ChannelFactories;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;

import static org.mockito.Mockito.verify;

public class TestRitualNotifier
{
    @Spy Listener _listener;

    RitualNotificationServer _rns;
    RitualNotificationClient _rnc;
    RitualNotifier _notifier;

    @Rule public TemporaryFolder _approotFolder;

    @Before
    public void setup() throws IOException
    {
        MockitoAnnotations.initMocks(this);

        _approotFolder = new TemporaryFolder();
        _approotFolder.create();
        AppRoot.set(_approotFolder.getRoot().getAbsolutePath());

        _notifier = new RitualNotifier();

        RitualNotificationSystemConfiguration config = new MockRNSConfiguration();
        _rns = new RitualNotificationServer(ChannelFactories.getServerChannelFactory(), _notifier,
                config);
        _rnc = new RitualNotificationClient(config);
    }

    @Test
    public void shouldNotifyListenersOnNewConnection()
            throws InterruptedException
    {
        _notifier.addListener(_listener);
        _rns.start_();
        _rnc.start();

        synchronized (_listener) {
            if (!_listener._triggered) {
                // give RNC 100ms to connect to RNS
                _listener.wait(1000);
            }
        }

        assert _listener._triggered;

        verify(_listener).onNotificationClientConnected();
    }

    private static class Listener implements IRitualNotificationClientConnectedListener
    {
        public boolean _triggered = false;

        @Override
        public void onNotificationClientConnected()
        {
            synchronized (this) {
                _triggered = true;
                notifyAll();
            }
        }
    }
}
