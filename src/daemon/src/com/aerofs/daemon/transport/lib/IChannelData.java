/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;

/**
 * Implemented by classes that provide basic identifying
 * information about a netty channel.
 */
public interface IChannelData
{
    DID getRemoteDID();

    UserID getRemoteUserID();
}
