/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.id.DID;

public interface IPullUpdatesListener
{
    void receivedPullUpdateFrom_(DID did);
}
