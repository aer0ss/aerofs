/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual_notification;

import com.aerofs.lib.nativesocket.NativeSocketAuthenticatorFactory;
import com.aerofs.lib.nativesocket.RitualNotificationSocketFile;
import com.aerofs.lib.nativesocket.UnixDomainSockPeerAuthenticator;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.testlib.AbstractTest;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@Ignore("https://aerofs.atlassian.net/browse/ENG-2230")
public class TestRitualNotifier extends AbstractTest
{
    SettableFuture<Boolean> connected;
    RitualNotificationServer _rns;
    RitualNotificationClient _rnc;

    @Mock RitualNotificationSocketFile _ritualNotificationSocketFile;
    @Mock UnixDomainSockPeerAuthenticator _authenticator;

    @Before
    public void setup()
            throws IOException, InterruptedException
    {
        TemporaryFolder tempRnsFolder = new TemporaryFolder();
        tempRnsFolder.create();
        File tmpRnsSocketFile = tempRnsFolder.newFile("temp_rns_TNS.sock");
        when(_ritualNotificationSocketFile.get()).thenReturn(tmpRnsSocketFile);
        _rns = new RitualNotificationServer(_ritualNotificationSocketFile,
                NativeSocketAuthenticatorFactory.create());
        _rnc = new RitualNotificationClient(_ritualNotificationSocketFile);
        connected = SettableFuture.create();
        _rns.addListener(() -> connected.set(true));

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
        bd.setType(Type.SHARED_FOLDER_JOIN);
        _rns.getRitualNotifier().sendNotification(bd.build());

        assertEquals(Type.SHARED_FOLDER_JOIN, done.get().getType());
    }
}