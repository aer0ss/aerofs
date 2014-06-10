/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.xray.client;

import org.jboss.netty.channel.Channel;

public interface IZephyrSignallingService
{
    // FIXME (AG): originator is not notified of errors!
    // FIXME (AG): I should simply send source/dest zid and rely on the service to serialize it
    // FIXME (AG): I'm also not a fan of including a reference to the originator in the method - it doesn't make sense
    void sendZephyrSignallingMessage(Channel originator, byte[] bytes);
}
