/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
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
 */
public class PresenceService implements IUnicastStateListener, IDeviceConnectionListener
{
    private static final Logger l = Loggers.getLogger(PresenceService.class);

    private final List<IDevicePresenceListener> listeners = newArrayList();
    private final Set<DID> onlineOnUnicast = newHashSet();

    public synchronized void addListener(IDevicePresenceListener listener)
    {
        listeners.add(listener);
    }

    // used only by unit tests
    synchronized boolean isPotentiallyAvailable(DID did)
    {
        return onlineOnUnicast.contains(did);
    }

    // used by tcp.Stores
    public synchronized boolean isConnected(DID did) { return onlineOnUnicast.contains(did); }

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
        if (onlineOnUnicast.add(did)) {
            notifyDevicePresence(did, true);
        }
    }

    @Override
    public synchronized void onDeviceDisconnected(DID did)
    {
        if (onlineOnUnicast.remove(did)) {
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
