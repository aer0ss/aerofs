/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;

/**
 * Delivers remote device presence notifications to classes that implement
 * {@link com.aerofs.daemon.transport.lib.IDevicePresenceListener}. Presence notifications
 * are sent whenever a device:
 * <ul>
 *     <li>Transitions from unavailable -> potentially available.</li>
 *     <li>Transitions from potentially available -> unavailable.</li>
 * </ul>
 * <br/>
 * Implements the presence rules outlined in "docs/design/transport/transport_presence_service_design.md".
 */
public final class PresenceService implements IMulticastListener, IUnicastListener, IDevicePresenceService
{
    private static final Logger l = Loggers.getLogger(PresenceService.class);

    private final List<IDevicePresenceListener> listeners = newArrayList();
    private final Set<DID> onlineOnUnicast = newHashSet();
    private final Set<DID> onlineOnMulticast = newHashSet();

    public synchronized void addListener(IDevicePresenceListener listener)
    {
        listeners.add(listener);
    }

    @Override
    public synchronized boolean isPotentiallyAvailable(DID did)
    {
        return onlineOnMulticast.contains(did) || onlineOnUnicast.contains(did);
    }

    public synchronized Set<DID> allPotentiallyAvailable()
    {
        Set<DID> potentiallyAvailablePeers = newHashSet(onlineOnMulticast);
        potentiallyAvailablePeers.addAll(onlineOnUnicast);
        return potentiallyAvailablePeers;
    }

    @Override
    public synchronized void onUnicastReady()
    {
        // noop for now
    }

    @Override
    public synchronized void onUnicastUnavailable()
    {
        // noop for now
    }

    @Override
    public synchronized void onDeviceConnected(DID did)
    {
        boolean added = onlineOnUnicast.add(did);
        if (added && !onlineOnMulticast.contains(did)) {
            notifyDevicePresence(did, true);
        }
    }

    @Override
    public synchronized void onDeviceDisconnected(DID did)
    {
        boolean removed = onlineOnUnicast.remove(did);

        // if the multicast service goes offline we delay
        // notifying anyone of presence changes until the unicast
        // connection goes offline as well. now that the
        // unicast connection is offline, it's time to clear
        // our state and notify everyone that the device is truly offline

        if (removed && !onlineOnMulticast.contains(did)) {
            notifyDevicePresence(did, false);
        }
    }

    @Override
    public synchronized void onMulticastReady()
    {
        // noop
    }

    @Override
    public synchronized void onMulticastUnavailable()
    {
        Set<DID> wasOnlineOnMulticast = newHashSet(onlineOnMulticast);
        onlineOnMulticast.clear();

        for (DID did : wasOnlineOnMulticast) {
            // FIXME (AG): I worry that during the listener callback the listener will remove itself from onlineOnUnicast
            if (!onlineOnUnicast.contains(did)) {
                notifyDevicePresence(did, false);
            }
        }
    }

    @Override
    public synchronized void onDeviceReachable(DID did)
    {
        boolean added = onlineOnMulticast.add(did);
        if (added && !onlineOnUnicast.contains(did)) {
            notifyDevicePresence(did, true);
        }
    }

    @Override
    public synchronized void onDeviceUnreachable(DID did)
    {
        boolean removed = onlineOnMulticast.remove(did);
        if (removed && !onlineOnUnicast.contains(did)) {
            notifyDevicePresence(did, false);
        }
    }

    private void notifyDevicePresence(DID did, boolean isPotentiallyAvailable)
    {
        l.info("{} {}", did, isPotentiallyAvailable ? "potentially available" : "unavailable");

        for (IDevicePresenceListener listener : listeners) {
            listener.onDevicePresenceChanged(did, isPotentiallyAvailable);
        }
    }
}
