/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;

import java.io.ByteArrayInputStream;

public interface IUnicastListener
{
    void onUnicastDatagramReceived(DID did, SID sid, ByteArrayInputStream is, int wirelen);

    void onStreamBegun(IIncomingStream stream);
}
