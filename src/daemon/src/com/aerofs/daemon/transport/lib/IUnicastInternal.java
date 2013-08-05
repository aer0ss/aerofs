/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;

/**
 * This interface is for internal use within the transport only
 */
public interface IUnicastInternal extends IUnicast
{
    public void disconnect(DID did, Exception cause);
}
