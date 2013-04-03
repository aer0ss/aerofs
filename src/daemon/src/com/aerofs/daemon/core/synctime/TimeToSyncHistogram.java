/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;

/**
 * A 3D histogram for time-to-sync:
 * for each remote device, and downloaded object, this histogram increments a time-to-sync bucket.
 */
interface TimeToSyncHistogram
{
    void update_(DID did, OID oid, TimeToSync sync);
}
