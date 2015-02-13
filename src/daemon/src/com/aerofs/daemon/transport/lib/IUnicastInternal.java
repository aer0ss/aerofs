/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;

/**
 * This interface is for internal use within the transport only
 * FIXME (AG): this interface is deprecated, and should not be used in new classes
 */
public interface IUnicastInternal extends IUnicast
{
    public void disconnect(DID did, Exception cause);
}