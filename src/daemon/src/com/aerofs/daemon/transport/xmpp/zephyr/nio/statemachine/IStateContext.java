/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.nio.statemachine;

import org.slf4j.Logger;

import java.util.Set;

/**
 * Represents the context that an {@link IState} object (i.e. state-function) uses
 * in its processing. Each {@code IStateContext} object can be thought of as containing
 * the transient, or in-progress stack/state as a system progresses through the
 * state machine.
 */
public interface IStateContext
{
    /** @return the current state this context object is in */
    public IState<?> curr_();

    /** sets the next state for this context object */
    public void next_(IState<?> next);

    /**
     * Dequeues the next <em>processable</em> event in this context object's pending-event queue
     * @see StateMachineSpec
     *
     * @param defer set of {@code IStateEventType} events that explicitly cannot be processed (i.e
     * should be ignored) in this state
     * @return {@link IStateEventType} the next <em>immediately-processable event</em>, or
     * {@code null} if no such event is available
     *
     */
    public StateMachineEvent dequeue_(Set<IStateEventType> defer);

    /** queues an event into the context object's pending-event queue */
    public void enqueue_(StateMachineEvent ev);

    /** @return the context object's {@link Logger} */
    public Logger logger_();
}
