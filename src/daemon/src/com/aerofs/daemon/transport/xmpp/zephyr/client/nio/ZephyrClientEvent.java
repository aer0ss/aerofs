/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio;

import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IStateEvent;

/**
 * Valid events for a ZephyrClient
 */
public enum ZephyrClientEvent implements IStateEvent
{
    BEGIN_CONNECT,
    PREPARED_FOR_CONNECT,
    SEL_CONNECT,
    CONNECTED,
    PREPARED_FOR_REGISTRATION,
    SEL_READ,
    REGISTERED,
    PENDING_OUT_PACKET,
    RECVD_REMOTE_CHAN_ID,
    PREPARED_FOR_BINDING,
    SEL_WRITE,
    BOUND,
}
