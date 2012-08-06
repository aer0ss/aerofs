/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine;

/**
 * Any object that implements this interface is a valid event that can be used in
 * a {@link StateMachine} transition map
 */
public interface IStateEvent
{
    // empty -> anything that implements this is automatically a valid event
}
