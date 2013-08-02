/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual_notification;

import com.aerofs.lib.AppRoot;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static com.aerofs.lib.ChannelFactories.getServerChannelFactory;
import static junit.framework.Assert.assertTrue;

public class TestRitualNotifier
{
    RitualNotificationServer _rns;
    RitualNotificationClient _rnc;

    @Rule public TemporaryFolder _approotFolder;

    @Before
    public void setup() throws IOException
    {
        MockitoAnnotations.initMocks(this);

        _approotFolder = new TemporaryFolder();
        _approotFolder.create();

        AppRoot.set(_approotFolder.getRoot().getAbsolutePath());

        RitualNotificationSystemConfiguration config = new MockRNSConfiguration();

        _rns = new RitualNotificationServer(getServerChannelFactory(), config);
        _rnc = new RitualNotificationClient(config);
    }

    @Test
    public void shouldNotifyListenersOnNewConnection()
            throws InterruptedException
    {
        Listener listener = new Listener();
        _rns.addListener(listener);

        _rns.start_();
        _rnc.start();

        // The intention of this test is to verify that RitualNotificationServer notifies its
        // listeners when a new connection is established
        //
        // Listener.waitForNotification will return "true" only when this condition is satisfied

        assertTrue(listener.waitForNotification(1000));
    }

    private static class Listener implements IRitualNotificationClientConnectedListener
    {
        private boolean _triggered = false;

        @Override
        public synchronized void onNotificationClientConnected()
        {
            _triggered = true;

            notifyAll();
        }

        synchronized boolean waitForNotification(long milliseconds)
                throws InterruptedException
        {
            if (!_triggered) wait(milliseconds);
            return _triggered;
        }
    }
}
