/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.id.OID;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Custom histogram for time-to-sync.
 *
 * There is a fixed number of time-to-sync bins (see {@link TimeToSync} for the distribution).
 * For long sync times, we want to know exactly which OIDs were behind the problem. For short
 * sync-times, we don't care. Thus for all sync times above some threshold, we record the frequency
 * per OID, and below that threshold, we record the frequency of the sync-time bin.
 */
class TimeToSyncHistogramSingleDID
{
    // TODO (MJ): automatic threshold adjustment (ArrowConfig perhaps??)
    private static final int THRESHOLD = TimeToSync.TOTAL_BINS * 7 / 8;

    private final int [] _subthreshold;

    // For the sync times that are over the threshold, we have higher resolution, counting the
    // the frequency for every *OID*, thus engineers can search the story line of those offending
    // OIDs
    private final List<Map<OID, Integer>> _overthreshold;

    TimeToSyncHistogramSingleDID()
    {
        _subthreshold = new int[THRESHOLD];

        int capacity = TimeToSync.TOTAL_BINS - THRESHOLD;
        _overthreshold = Lists.newArrayListWithCapacity(capacity);
        for (int i = 0; i < capacity; i++) _overthreshold.add(null);
        checkState(_overthreshold.size() == capacity);
    }

    void update_(OID oid, TimeToSync timeToSync)
    {
        int bin = timeToSync.toBinIndex();

        if (bin < THRESHOLD) {
            _subthreshold[bin]++;
        } else {
            updateOverThreshold_(oid, bin);
        }
    }

    int frequencyAtBin(int bin)
    {
        checkArgument(0 <= bin && bin < TimeToSync.TOTAL_BINS);

        if (bin < THRESHOLD) {
            return _subthreshold[bin];
        } else {
            return frequencyOverThreshold_(bin);
        }
    }

    private int frequencyOverThreshold_(int bin)
    {
        int index = bin - THRESHOLD;
        Map<OID, Integer> freqs = _overthreshold.get(index);
        int sum = 0;
        if (freqs != null) for (Integer f : freqs.values()) sum += f;
        return sum;
    }

    private void updateOverThreshold_(OID oid, int bin)
    {
        int index = bin - THRESHOLD;
        Map<OID, Integer> frequencies = _overthreshold.get(index);
        if (frequencies == null) {
            frequencies = Maps.newHashMap();
            _overthreshold.set(index, frequencies);
        }

        Integer f = frequencies.get(oid);
        if (f == null) frequencies.put(oid, 1);
        else frequencies.put(oid, f + 1);
    }
}
