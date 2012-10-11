/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine;

/**
 * Any object that implements this interface is a state-processing function
 * that can be used to populate the transition map described by {@link StateMachineSpec}
 * for a {@link StateMachine}.
 *
 * @param <T> {@link IStateContext} object used to hold system-state
 */
public interface IState<T extends IStateContext>
{
    /**
     * Processing function
     *
     * @param ev {@link StateMachineEvent}
     * @param ctx holds the context required to do the processing
     * @return  {@code StateMachineEvent} generated as a result of processing in this state
     * (this is an <em>internally</em> generated event)
     */
    StateMachineEvent process_(StateMachineEvent ev, T ctx);

    /**
     * @return human-readable name for the state function
     */
    String shortname_();
}
