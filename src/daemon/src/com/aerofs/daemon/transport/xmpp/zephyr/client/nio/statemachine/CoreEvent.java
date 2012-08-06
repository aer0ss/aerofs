/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine;

/**
 * These are the only two events that are returned by an {@link IState} that a
 * {@link StateMachine} specifically understands. Can be used by an IState function
 * to wait for an external event to appear or terminate state machine operation.
 */
public enum CoreEvent implements IStateEvent
{
    /**
     * should be returned whenever you want to wait for an external IO or
     * asynchronous event. Basically parks the state machine until it is run again
     */
    PARK,

    /**
     * returned when you want to forcibly halt the state machine <em>regardless</em>
     * of what state you are currently in. this is the big "shutdown now" hammer
     */
    HALT
}
