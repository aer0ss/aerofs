/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.online_status;

import com.aerofs.daemon.core.notification.ISnapshotableNotificationEmitter;
import com.aerofs.daemon.core.notification.Notifications;
import com.aerofs.daemon.lib.CoreExecutor;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.aerofs.verkehr.client.wire.ConnectionListener;
import com.aerofs.verkehr.client.wire.VerkehrPubSubClient;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;

import java.net.NetworkInterface;
import java.util.concurrent.Executor;

/**
 * The purpose of this object is to listen to connection status of various services, determine
 *   whether the device is online, and to notify the GUI if the online status changes.
 *
 * Functionally:
 *   - Online status is defined as being connected to Verkehr and at least one network interface
 *       is up.
 *   - Listens to VerkehrNotificationSubscriber for online/offline.
 *   - Listens to LinkStateService for online/offline.
 *   - Notifies the GUI via ritual notification.
 *   - Sends a snapshot via ritual notification when a ritual notification client connects.
 *
 * N.B. there are subtle threading issues involved. By default:
 *   - The Verkehr listener is called on the Verkehr's netty thread.
 *   - The link state listener is called on the link state service's own Java daemon thread.
 *   - EISendsnapshot calls sendOnlineStatusNotification_ on core thread.
 *
 * To simplify concurrent access, we use CoreExecutor with both Verkehr and LinkState so now
 * all calls through checkStates_ and sendOnlineStatusNotification_ are made on the core thread,
 * which makes unsafe concurrent access a non-issue.
 *
 * As a side effect, if the core queue is full, the callback threads for Verkehr and LinkState
 * are blocked until the event is successfully enqueued on the core queueu.
 */
public class OnlineStatusNotifier implements ISnapshotableNotificationEmitter
{
    private final VerkehrPubSubClient _vk;
    private final LinkStateService _lss;
    private final RitualNotificationServer _rns;
    private final Executor _executor;

    protected boolean _isOnline;
    protected boolean _isVerkehrConnected;
    protected boolean _isLinkStateConnected;

    @Inject
    public OnlineStatusNotifier(VerkehrPubSubClient vk, LinkStateService lss, RitualNotificationServer rns, CoreExecutor executor)
    {
        _vk = vk;
        _lss = lss;
        _rns = rns;
        _executor = executor;
    }

    public void init_()
    {
        _vk.addConnectionListener(new ConnectionListener()
        {
            @Override
            public void onConnected(VerkehrPubSubClient client)
            {
                checkStates_(true, _isLinkStateConnected);
            }

            @Override
            public void onDisconnected(VerkehrPubSubClient client)
            {
                checkStates_(false, _isLinkStateConnected);
            }
        }, _executor);

        _lss.addListener(new ILinkStateListener()
        {
            @Override
            public void onLinkStateChanged(
                    ImmutableSet<NetworkInterface> previous,
                    ImmutableSet<NetworkInterface> current,
                    ImmutableSet<NetworkInterface> added,
                    ImmutableSet<NetworkInterface> removed)
            {
                checkStates_(_isVerkehrConnected, !current.isEmpty());
            }
        }, _executor);
    }

    protected void checkStates_(boolean verkehrConnected, boolean linkStateConnected)
    {
        _isVerkehrConnected = verkehrConnected;
        _isLinkStateConnected = linkStateConnected;

        boolean isOnline = _isVerkehrConnected && _isLinkStateConnected;

        if (_isOnline != isOnline) {
            _isOnline = isOnline;
            sendOnlineStatusNotification_();
        }
    }

    public void sendOnlineStatusNotification_()
    {
        _rns.getRitualNotifier().sendNotification(Notifications.newOnlineStatusChangedNotification(_isOnline));
    }

    @Override
    public final void sendSnapshot_()
    {
        sendOnlineStatusNotification_();
    }
}
