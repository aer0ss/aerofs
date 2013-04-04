/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.google.common.collect.Maps;

import java.util.Map;

class MultiDeviceTimeToSyncHistogram implements TimeToSyncHistogram
{
    private final Map<DID, TimeToSyncHistogramSingleDID> _histograms = Maps.newHashMap();

    @Override
    public void update_(DID did, OID oid, TimeToSync sync)
    {
        TimeToSyncHistogramSingleDID histogram = _histograms.get(did);
        if (histogram == null) {
            histogram = new TimeToSyncHistogramSingleDID();
            _histograms.put(did, histogram);
        }

        histogram.update_(oid, sync);
    }
}
