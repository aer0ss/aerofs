/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.nio.statemachine;

/**
 * Thrown by {@link StateMachine} when an {@link IState} generates an
 * {@link IStateEvent} for which there is no valid transition defined in the
 * supplied transition map
 */
public class ExInvalidTransition extends Exception
{
    public ExInvalidTransition(IState<?> prevState, StateMachineEvent ev)
    {
        _state = prevState;
        _event = ev;
    }

    public IState<?> getState()
    {
        return _state;
    }

    public StateMachineEvent getEvent()
    {
        return _event;
    }

    /** state that generated the invalid transition event */
    private final IState<?> _state;

    /** the invalid transition event */
    private final StateMachineEvent _event;

    /** serialization id */
    private static final long serialVersionUID = 1L;
}
