/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.online_status;

import com.aerofs.daemon.core.notification.ISnapshotableNotificationEmitter;
import com.aerofs.daemon.core.notification.Notifications;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.aerofs.ssmp.SSMPClient.ConnectionListener;
import com.aerofs.ssmp.SSMPConnection;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;

/**
 * The purpose of this object is to listen to connection status of various services, determine
 *   whether the device is online, and to notify the GUI if the online status changes.
 *
 * Functionally:
 *   - Online status is defined as being connected to lipwig and at least one network interface
 *       is up.
 *   - Listens to SSMPConnection for online/offline.
 *   - Listens to LinkStateService for online/offline.
 *   - Notifies the GUI via ritual notification.
 *   - Sends a snapshot via ritual notification when a ritual notification client connects.
 *
 * N.B. there are subtle threading issues involved. By default:
 *   - The SSMP listener is called on the SSMPConnection's netty thread.
 *   - The link state listener is called on the link state service's own Java daemon thread.
 *   - EISendSnapshot calls sendOnlineStatusNotification on core thread.
 *
 * To avoid concurrency issues, access to the aggregate online status field and emission of ritual
 * notifications is synchronized by the object monitor.
 */
public class OnlineStatusNotifier implements ISnapshotableNotificationEmitter
{
    private final SSMPConnection _ssmp;
    private final LinkStateService _lss;
    private final RitualNotificationServer _rns;

    // synchronized(this)
    protected boolean _isOnline;

    protected boolean _isSSMPConnected;
    protected boolean _isLinkStateConnected;

    @Inject
    public OnlineStatusNotifier(SSMPConnection ssmp, LinkStateService lss,
                                RitualNotificationServer rns)
    {
        _ssmp = ssmp;
        _lss = lss;
        _rns = rns;
    }

    public void init_()
    {
        _ssmp.addConnectionListener(new ConnectionListener() {
            @Override
            public void connected() {
                _isSSMPConnected = true;
                checkStates();
            }

            @Override
            public void disconnected() {
                _isSSMPConnected = false;
                checkStates();
            }
        });

        _lss.addListener((previous, current, added, removed) -> {
            _isLinkStateConnected = !current.isEmpty();
            checkStates();
        }, MoreExecutors.sameThreadExecutor());
    }

    protected synchronized void checkStates()
    {
        boolean isOnline = _isSSMPConnected && _isLinkStateConnected;

        if (_isOnline != isOnline) {
            _isOnline = isOnline;
            sendOnlineStatusNotification();
        }
    }

    public void sendOnlineStatusNotification()
    {
        _rns.getRitualNotifier()
                .sendNotification(Notifications.newOnlineStatusChangedNotification(_isOnline));
    }

    @Override
    public final synchronized void sendSnapshot_()
    {
        sendOnlineStatusNotification();
    }
}
