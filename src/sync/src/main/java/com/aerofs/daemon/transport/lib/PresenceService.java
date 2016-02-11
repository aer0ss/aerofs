/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.event.net.EIDevicePresence;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.ids.DID;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import org.slf4j.Logger;

import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.aerofs.lib.event.Prio.LO;
import static com.google.common.collect.Sets.newHashSet;

/**
 * Delivers remote device presence notifications to the core queue and classes that implement
 * {@link com.aerofs.daemon.transport.lib.IDevicePresenceListener}
 *
 * Presence notifications are sent whenever a device:
 * <ul>
 *     <li>Transitions from unavailable -> potentially available.</li>
 *     <li>Transitions from potentially available -> unavailable.</li>
 * </ul>
 * <br/>
 */
public class PresenceService implements IUnicastStateListener, IDeviceConnectionListener
{
    private static final Logger l = Loggers.getLogger(PresenceService.class);

    private final ITransport tp;
    private final IBlockingPrioritizedEventSink<IEvent> sink;

    private final Set<DID> onlineOnUnicast = newHashSet();

    private final Executor _executor;
    private final Deque<IDevicePresenceListener> listeners = new ConcurrentLinkedDeque<>();

    public PresenceService(ITransport tp, IBlockingPrioritizedEventSink<IEvent> sink) {
        // call listeners in a single thread executor to:
        //  - avoid deadlocks
        //  - ensure a sane ordering of notifications is preserved
        this(tp, sink, Executors.newSingleThreadExecutor(r -> new Thread(r, "ps")));
    }

    // allow custom executor for unit tests
    PresenceService(ITransport tp, IBlockingPrioritizedEventSink<IEvent> sink, Executor executor)
    {
        this.tp = tp;
        this.sink = sink;
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
            sink.enqueueBlocking(new EIDevicePresence(tp, isPotentiallyAvailable, did), LO);
            for (IDevicePresenceListener listener : listeners) {
                listener.onDevicePresenceChanged(did, isPotentiallyAvailable);
            }
        });
    }
}
