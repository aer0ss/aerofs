/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual_notification;

import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.testlib.AbstractTest;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.aerofs.lib.ChannelFactories.getServerChannelFactory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestRitualNotifier extends AbstractTest
{
    SettableFuture<Boolean> connected;
    RitualNotificationServer _rns;
    RitualNotificationClient _rnc;

    @Before
    public void setup() throws IOException
    {
        RitualNotificationSystemConfiguration config = new MockRNSConfiguration();

        _rns = new RitualNotificationServer(getServerChannelFactory(), config);
        _rnc = new RitualNotificationClient(config);

        connected = SettableFuture.create();
        _rns.addListener(new IRitualNotificationClientConnectedListener() {
            @Override
            public synchronized void onNotificationClientConnected()
            {
                connected.set(true);
            }
        });

        _rns.start_();
        _rnc.start();
    }

    @After
    public void tearDown() throws Exception
    {
        _rns.stop_();
        _rnc.stop();
    }

    @Test
    public void shouldNotifyServerListenersOnNewConnection() throws Exception
    {
        // The intention of this test is to verify that RitualNotificationServer notifies its
        // listeners when a new connection is established
        //
        // Listener.waitForNotification will return "true" only when this condition is satisfied

        assertTrue(connected.get(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shouldNotifyClientListenersOnDisconnection() throws Exception
    {
        final SettableFuture<Boolean> done = SettableFuture.create();
        _rnc.addListener(new IRitualNotificationListener() {
            @Override
            public void onNotificationReceived(PBNotification notification)
            {
                done.set(false);
            }

            @Override
            public void onNotificationChannelBroken()
            {
                done.set(true);
            }
        });

        assertTrue(connected.get());

        _rns.stop_();

        assertTrue(done.get());
    }

    @Test
    public void shouldNotifyClientListenersOnNotification() throws Exception
    {
        final SettableFuture<PBNotification> done = SettableFuture.create();
        _rnc.addListener(new IRitualNotificationListener() {
            @Override
            public void onNotificationReceived(PBNotification notification)
            {
                done.set(notification);
            }

            @Override
            public void onNotificationChannelBroken()
            {
                done.set(null);
            }
        });

        PBNotification.Builder bd = PBNotification.newBuilder();
        bd.setType(Type.BAD_CREDENTIAL);
        _rns.getRitualNotifier().sendNotification(bd.build());

        assertEquals(Type.BAD_CREDENTIAL, done.get().getType());
    }
}
