/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.serverstatus;

import com.aerofs.daemon.core.notification.Notifications;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.IServiceStatusListener;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.Server;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

/**
 * The purpose of this object is to listen to server connection status and notify ritual
 *   notification clients if server connection status changes. It is edge-triggered and
 *   will send notification only when RNC connects initially and when connection status
 *   changed.
 *
 * Currently, it only cares about connection status to Verkehr.
 */
public class ServerStatusNotifier implements IServiceStatusListener
{
    private final ServerConnectionStatus _scs;
    private final RitualNotificationServer _rns;

    @Inject
    public ServerStatusNotifier(ServerConnectionStatus scs, RitualNotificationServer rns)
    {
        _scs = scs;
        _rns = rns;
    }

    public void start()
    {
        _scs.addListener(this, Server.VERKEHR);
    }

    public void sendServerStatusNotification()
    {
        sendNotification(_scs.isConnected(Server.VERKEHR));
    }

    protected void sendNotification(boolean isOnline)
    {
        _rns.getRitualNotifier()
                .sendNotification(Notifications.newServerStatusChangedNotification(isOnline));
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
