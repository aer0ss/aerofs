/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import org.slf4j.Logger;

import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
 *
 * FIXME: the entire presence flow is long overdue for a refactor
 */
public class PresenceService implements IUnicastStateListener, IDeviceConnectionListener
{
    private static final Logger l = Loggers.getLogger(PresenceService.class);

    private final Deque<IDevicePresenceListener> listeners = new ConcurrentLinkedDeque<>();
    private final Set<DID> onlineOnUnicast = newHashSet();

    private final Executor _executor;

    public PresenceService()
    {
        // call listeners in a single thread executor to:
        //  - avoid deadlocks
        //  - ensure a sane ordering of notifications is preserved
        this(Executors.newSingleThreadExecutor(r -> new Thread(r, "ps")));
    }

    // allow custom executor for unit tests
    PresenceService(Executor executor)
    {
        _executor = executor;
    }

    public void addListener(IDevicePresenceListener listener)
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
    public void onUnicastReady()
    {
        // noop for now
    }

    @Override
    public void onUnicastUnavailable()
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

        _executor.execute(() -> {
            for (IDevicePresenceListener listener : listeners) {
                listener.onDevicePresenceChanged(did, isPotentiallyAvailable);
            }
        });
    }
}
