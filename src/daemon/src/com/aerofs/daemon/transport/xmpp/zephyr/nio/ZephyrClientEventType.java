/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.nio;

import com.aerofs.daemon.transport.xmpp.zephyr.nio.statemachine.IStateEventType;

/**
 * Valid {@link IStateEventType} events for a Zephyr client
 */
public enum ZephyrClientEventType implements IStateEventType
{
    BEGIN_CONNECT,
    PREPARED_FOR_CONNECT,
    SEL_CONNECT,
    CONNECTED,
    PREPARED_FOR_REGISTRATION,
    SEL_READ,
    REGISTERED,
    PENDING_OUT_PACKET,
    RECVD_SYN,
    RECVD_SYNACK,
    RECVD_ACK,
    SENT_SYN,
    SENT_SYNACK,
    HANDSHAKE_COMPLETE,
    PREPARED_FOR_BINDING,
    SEL_WRITE,
    BOUND,
    WRITE_COMPLETE,
}
