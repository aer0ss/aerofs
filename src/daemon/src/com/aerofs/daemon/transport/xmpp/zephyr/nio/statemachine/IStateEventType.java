/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.nio.statemachine;

/**
 * Any object that implements this interface is a valid event-type that can be used in
 * a {@link StateMachine} transition map described by {@link StateMachineSpec}
 */
public interface IStateEventType
{
    // empty -> anything that implements this is automatically a valid event type
}
