/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import org.jboss.netty.channel.Channel;

/**
 * Interface for any class that requires an updated Channel instance as a result of
 * a reconnect action.
 */
interface IConnectNotifier
{
    /**
     * Notification that the given Channel instance is connected, and replaces any previous
     * Channel object.
     */
    void channelConnected(Channel c);
}
