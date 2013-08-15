/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;

public interface IPresenceManager
{
    boolean isPresent(DID did);
}
