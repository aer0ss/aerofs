/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine;

import org.apache.log4j.Logger;

/**
 * Represents the context that an {@link IState} object (i.e. state-function) uses
 * in its processing. Each IStateContext object can be thought of as containing
 * the transient, or in-progress stack/state as a system progresses through the
 * state machine.
 */
public interface IStateContext
{
    /** returns the current state this context object is in */
    public IState<?> curr_();

    /** sets the next state for this context object */
    public void next_(IState<?> next);

    /** dequeues the next event in this context object's pending-event queue */
    public IStateEvent dequeue_();

    /** queues an event into the context object's pending-event queue */
    public void enqueueEvent_(IStateEvent ev);

    /** returns the context object's {@link Logger} */
    public Logger logger_();
}
