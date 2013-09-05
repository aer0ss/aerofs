/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.tray;

import com.aerofs.gui.GUI;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ritual_notification.RitualNotificationClient;
import org.eclipse.swt.widgets.Widget;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.*;

/**
 * This class is created to address a race condition in ServerStatus.
 *
 * The ritual notification system is used for the daemon to actively broadcast its various states
 * and notify the GUI widgets that are interested in these states.
 *
 * Note that the notification system does not track states, it merely broadcast it. Thus we have a
 * race condition between the widgets and the notification system.
 *
 * If the widgets are setup before the notification system, they will receive every single
 * notification and be able to construct the final state they wish to present to the users.
 *
 * On the other hand, if the notification system is set up before the widgets, the widgets may
 * miss some notifications and fail to construct the correct state.
 *
 * Note that there are situations where the intended listener, the widget, is not ready when the
 * RNS starts broadcasting notifications. Hence another object is needed to maintain the state
 * and the widgets will listen to that object instead.
 *
 * Mechanics:
 * The cache is created before the ritual notification client starts, it maintains the server
 * status based on the notifications.
 *
 * Whenever a widget subscribes to the cache, the cache enqueues an event to notify the widget of
 * the current state. Whenever the cache receives a notification from RNC, the notification
 * is processed on RNC's thread and then an event is queued to notify the widget on the UI
 * thread.
 *
 * N.B. this class is a cache for server status much like TransferState is a cache for transfer
 * notifications.
 *
 * TODO (AT): this is the 2nd time a cache for ritual notification is created. we should consolidate
 * the design if we ever need to create a cache for a different type of ritual notification.
 */
public class ServerStatusCache implements IRitualNotificationListener
{
    private final Notifier _notifier;
    private final RitualNotificationClient _rnc;

    private boolean _online;

    private Widget _widget;
    // we only need to support one listener for the time being
    private volatile IServerStatusListener _listener;

    /**
     * @param rnc - the RNC to listen to.
     * @pre RNC hasn't started yet
     */
    public ServerStatusCache(RitualNotificationClient rnc)
    {
        _notifier = new Notifier();

        _rnc = rnc;
        _rnc.addListener(this);
    }

    /**
     * Sets the server status listener and enqueues a notification on system event queue.
     * The intention is that the listener will be set once and never change.
     *
     * @param listener - the listener for this cache
     * @param widget - the associated widget that's listening to this cache
     * @pre ServerStatusListener has never been set before.
     */
    public void setListener(@Nonnull Widget widget, @Nonnull IServerStatusListener listener)
    {
        checkState(_listener == null);
        checkNotNull(widget);
        checkNotNull(listener);

        _widget = widget;
        _listener = listener;

        notifyListener();
    }

    private void notifyListener()
    {
        if (_listener != null) GUI.get().safeAsyncExec(_widget, _notifier);
    }

    @Override
    public void onNotificationReceived(PBNotification notification)
    {
        if (notification.getType() == Type.SERVER_STATUS_CHANGED) {
            _online = notification.getServerStatus();
            notifyListener();
        }
    }

    @Override
    public void onNotificationChannelBroken()
    {
        // assume the latest state is correct and will continue to be correct until the
        // notification channel is re-stablished and the snapshot tells us otherwise
    }

    public static interface IServerStatusListener
    {
        void onServerStatusChanged(boolean online);
    }

    private class Notifier implements Runnable
    {
        @Override
        public void run()
        {
            // N.B. since _online refers to the class field, this impl means that we
            // evaluate _online when the _listener's callback is invoked.
            _listener.onServerStatusChanged(_online);
        }
    }
}
