package com.aerofs.daemon.transport.xmpp.zephyr.client.netty;

import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.OutArg;

/**
 * An abstract representation of an event-loop. Events can be enqueued by calling {@link
 * #enqueueEvent(Object, Prio)}.
 * <p/>
 * The event-loop runs in the {@link #run()} method and is typically started in a {@link Thread} or
 * {@link Executor}. If the subclass needs to perform initialization or cleanup, it may override
 * {@link #run()}, but should call the super implementation to enter the actual event loop.
 * <p/>
 * At a minimum. the subclass need only to override {@link #onEvent(Object)}, which receives events
 * from the event-queue in a serial, single threaded manner.
 *
 * @param <T> The type of the event, processed in {@link #onEvent(Object)}
 */
public abstract class AbstractEventLoop<T> implements Runnable
{
    private final BlockingPrioQueue<T> _eventQueue;
    private Thread _thread;

    public AbstractEventLoop(int queueCapacity)
    {
        _eventQueue = new BlockingPrioQueue<T>(queueCapacity);
        _thread = null;
    }

    /**
     * Enqueues an event on to the Event Loop for processing on the main event loop thread. The
     * event will be delivered to {@link AbstractEventLoop#onEvent(Object)}. This call does not
     * block.
     *
     * @param event Event to enqueue
     * @param priority The priority of the event
     */
    protected void enqueueEvent(T event, Prio priority)
    {
        _eventQueue.enqueueBlocking(event, priority);
    }

    /**
     * Throws an assertion exception if the current thread <strong>is not</strong> the designated
     * event thread
     */
    protected final void assertEventThread()
    {
        assert Thread.currentThread() == _thread : ("Execution not occuring on event thread");
    }

    /**
     * Throws an assertion exception if the current thread <strong>is</strong> the designated event
     * thread
     */
    protected final void assertNotEventThread()
    {
        assert Thread.currentThread() != _thread : ("Execution occuring on event thread");
    }

    /**
     * The main method that runs the event loop. This should be called only by the thread that runs
     * the event loop. Subclasses overriding this class should call <code>super.run()</code> to
     * start the loop
     */
    @Override
    public void run()
    {
        // Associate the thread running this method as the main event thread
        _thread = Thread.currentThread();

        OutArg<Prio> priority = new OutArg<Prio>();
        while (true) {
            boolean done = onEvent(_eventQueue.dequeue(priority));
            if (done) {
                break;
            }
        }
    }

    /**
     * Returns the thread that is running this event loop
     *
     * @return The thread running the event loop, or null if the event loop hasn't been started
     */
    public Thread getThread()
    {
        return _thread;
    }

    /**
     * Enqueued events are handled here. This method is guaranteed to be called from the main event
     * loop thread and should not be called manually
     *
     * @param event Event to process
     * @return <strong>true</strong> if the event loop should exit, <strong>false</strong>
     *         otherwise
     */
    protected abstract boolean onEvent(T event);
}
