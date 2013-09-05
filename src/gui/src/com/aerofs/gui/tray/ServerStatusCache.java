/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.tray;

import com.aerofs.gui.GUI;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ritual_notification.RitualNotificationClient;
import com.google.common.base.Preconditions;
import org.eclipse.swt.widgets.Widget;

import javax.annotation.Nonnull;

/**
 * This class is created to address a race condition and an emerging architectural problem related
 * to ServerStatus, and to certain extents, RitualNotification.
 *
 * RitualNotificationServer (RNS) publishes a snapshot when a client connects to it, and it's
 * designed to work with one RitualNotificationClient (RNC).
 *
 * Now the end users of these notifications (UI widgets) may not be ready when RNC connects to RNS.
 * So it's become apparent that some intermediate cache is necessary so the end user will be able
 * to re-construct the final state. TransferState is a cache for transfer notifications, and this
 * class is a cache for server status.
 *
 * TODO (AT): this is the 2nd time a cache for ritual notification is created, we should consolidate
 * the design if we ever need to create a cache for a different type of ritual notification.
 */
public class ServerStatusCache implements IRitualNotificationListener
{
    protected final Notifier _notifier;
    protected final RitualNotificationClient _rnc;

    protected boolean _online = false;

    protected Widget _widget;
    // we only support one listener because that's all we need to
    protected IServerStatusListener _listener;

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
     * @param listener
     * @pre ServerStatusListener has never been set before.
     */
    public synchronized void setListener(@Nonnull Widget widget,
            @Nonnull IServerStatusListener listener)
    {
        Preconditions.checkArgument(_listener == null);

        _widget = widget;
        _listener = listener;

        notifyListener();
    }

    // N.B. notifies the listener to poll the latest state, the synchronized keyword is intended
    // to guard access to _listener.
    protected synchronized void notifyListener()
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

    protected class Notifier implements Runnable
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
