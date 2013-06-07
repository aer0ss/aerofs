/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.base.id.DID;

import java.io.ByteArrayInputStream;

public interface IUnicastListener
{
    void onUnicastDatagramReceived(DID did, ByteArrayInputStream is, int wirelen);

    void onStreamBegun(IIncomingStream stream);
}
