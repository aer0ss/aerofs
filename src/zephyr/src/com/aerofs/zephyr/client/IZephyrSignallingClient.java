/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.zephyr.client;

import com.aerofs.zephyr.client.exceptions.ExHandshakeFailed;
import com.aerofs.zephyr.proto.Zephyr.ZephyrHandshake;

public interface IZephyrSignallingClient
{
    public void processIncomingZephyrSignallingMessage(ZephyrHandshake incoming) throws ExHandshakeFailed;
}
