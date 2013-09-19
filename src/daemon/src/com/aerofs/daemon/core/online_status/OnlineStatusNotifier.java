/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.online_status;

import com.aerofs.daemon.core.notification.Notifications;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.IServiceStatusListener;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.Server;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

/**
 * The purpose of this object is to listen to connection status to various services, determine
 *   whether the device is online, and to notify the GUI if the online status changes.
 *
 * Functionally:
 *   - Online status is defined as being connected to Verkehr.
 *   - Listens to VerkehrNotificationSubscriber for online/offline.
 *   - Notifies the GUI via ritual notification.
 *   - Sends a snapshot via ritual notification when a ritual notification client connects.
 */
public class OnlineStatusNotifier implements IServiceStatusListener
{
    private final ServerConnectionStatus _scs;
    private final RitualNotificationServer _rns;

    @Inject
    public OnlineStatusNotifier(ServerConnectionStatus scs, RitualNotificationServer rns)
    {
        _scs = scs;
        _rns = rns;
    }

    public void init()
    {
        _scs.addListener(this, Server.VERKEHR);
    }

    public void sendOnlineStatusNotification()
    {
        sendNotification(_scs.isConnected(Server.VERKEHR));
    }

    protected void sendNotification(boolean isOnline)
    {
        _rns.getRitualNotifier()
                .sendNotification(Notifications.newOnlineStatusChangedNotification(isOnline));
    }

    @Override
    public boolean isAvailable(ImmutableMap<Server, Boolean> statuses)
    {
        return statuses.get(Server.VERKEHR);
    }

    @Override
    public void available()
    {
        sendNotification(true);
    }

    @Override
    public void unavailable()
    {
        sendNotification(false);
    }
}
