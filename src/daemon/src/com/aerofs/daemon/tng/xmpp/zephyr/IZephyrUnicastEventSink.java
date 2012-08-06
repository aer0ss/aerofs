/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr;

import org.jboss.netty.buffer.ChannelBuffer;

public interface IZephyrUnicastEventSink
{
    void onChannelRegisteredWithZephyr_(int zid);

    void onDataReceivedFromChannel_(ChannelBuffer data);
}
