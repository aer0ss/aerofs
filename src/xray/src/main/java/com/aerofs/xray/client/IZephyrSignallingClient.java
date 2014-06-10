/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.xray.client;

import com.aerofs.xray.client.exceptions.ExHandshakeFailed;
import com.aerofs.xray.proto.XRay.ZephyrHandshake;

public interface IZephyrSignallingClient
{
    public void processIncomingZephyrSignallingMessage(ZephyrHandshake incoming) throws ExHandshakeFailed;
}
