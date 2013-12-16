/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.ex.ExNoResource;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.transport.TransportThreadGroup;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.google.common.collect.Queues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Helper class that manages the core->transport event queue for a transport.
 * <p/>
 * This class consists of two components:
 * <ol>
 *     <li>A blocking queue.</li>
 *     <li>An event-processing thread.</li>
 * </ol>
 * In normal operation the core enqueues an event to be processed into
 * the blocking queue. The event-processing thread dequeues the event
 * and runs its associated handler, i.e. processing the event.
 * <p/>
 * This class can be embedded in a transport implementation (TCP, Zephyr, Jingle, <em>etc.</em>).
 */
public final class TransportEventQueue implements IBlockingPrioritizedEventSink<IEvent>
{
    private static final Logger l = LoggerFactory.getLogger(TransportEventQueue.class);

    private final LinkedBlockingQueue<IEvent> eventQueue = Queues.newLinkedBlockingQueue(DaemonParam.QUEUE_LENGTH_DEFAULT);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final String eventQueueReaderId;
    private final EventDispatcher dispatcher;
    private final Thread eventQueueReaderThread;

    public TransportEventQueue(String transportId, EventDispatcher dispatcher)
    {
        this.eventQueueReaderId = transportId + "-eq";
        this.dispatcher = dispatcher;
        this.eventQueueReaderThread = new Thread(TransportThreadGroup.get(), new Runnable() {
            @Override
            public void run()
            {
                l.info("{}: starting", eventQueueReaderId);

                try {
                    while (running.get()) {
                        IEvent ev = eventQueue.take();
                        TransportEventQueue.this.dispatcher.dispatch_(ev, Prio.LO);
                    }
                } catch (InterruptedException e) {
                    l.warn("{}: interrupted during poll", eventQueueReaderId, e);
                }
            }
        }, eventQueueReaderId);

        eventQueueReaderThread.setDaemon(false);
    }

    public void start()
    {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        eventQueueReaderThread.start();
    }

    public void stop()
    {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        try {
            eventQueueReaderThread.interrupt();
            eventQueueReaderThread.join();
        } catch (InterruptedException e) {
            l.warn("{}: interrupted while waiting for reader thread to shutdown", eventQueueReaderId, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <strong>WARNING:</strong> Since the core can call this method before
     * the transport event-queue thread has started this method can block indefinitely.
     * This can happen because this method blocks until the transport event
     * queue has space; but, if the event-processing thread is not running then
     * a full queue will never be processed.
     * <p/>
     * Proper solution? Don't call this method before starting the transport.
     */
    @Override
    public void enqueueBlocking(IEvent ev, Prio prio)
    {
        // FIXME (AG): commented out because the core enqueues events before the transport starts
        // checkState(running.get());

        try {
            eventQueue.put(ev);
        } catch (InterruptedException e) {
            throw new IllegalStateException("interrupted during blocking enqueue", e);
        }
    }

    @Override
    public void enqueueThrows(IEvent ev, Prio prio)
            throws ExNoResource
    {
        // FIXME (AG): commented out because the core enqueues events before the transport starts
        // checkState(running.get());

        boolean enqueued = eventQueue.offer(ev);
        if (!enqueued) {
            throw new ExNoResource("cannot enqueue " + ev + " at " + prio);
        }
    }

    @Override
    public boolean enqueue(IEvent ev, Prio prio)
    {
        // FIXME (AG): commented out because the core enqueues events before the transport starts
        // checkState(running.get());

        return eventQueue.offer(ev);
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        int available = eventQueue.remainingCapacity();
        int capacity = eventQueue.size();
        ps.println("available:" + available + " capacity:" + capacity);
    }
}
