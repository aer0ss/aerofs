/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.id.OID;
import com.aerofs.synctime.api.ClientSideHistogram;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.Arrays;
import java.util.Iterator;
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
class SingleDIDTimeToSyncHistogram
{
    // TODO (MJ): automatic threshold adjustment (ArrowConfig perhaps??)
    // For now, since the server doesn't record OIDs anyway, set the threshold to the total
    // number of bins, to save client memory
    // TODO (MJ): when using the overthreshold mpas, consider limiting the total capacity
    // of the Histogram to avoid OOMs (perhaps by invoking a POST to the server)
    // Also move this variable to Params.java. At this point there is no need
    private static final int THRESHOLD = TimeToSync.TOTAL_BINS;

    private final int [] _subthreshold;

    // For the sync times that are over the threshold, we have higher resolution, counting the
    // the frequency for every *OID*, thus engineers can search the story line of those offending
    // OIDs
    private final List<Map<OID, Integer>> _overthreshold;

    SingleDIDTimeToSyncHistogram()
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
        if (bin < THRESHOLD) {
            return _subthreshold[bin];
        } else {
            return frequencyOverThreshold_(bin);
        }
    }

    ClientSideHistogram toJSONableHistogram()
    {
        int [] counts = new int[size()];
        // TODO (MJ) this obliterates any chance of SIMD
        for (int i = 0; i < counts.length; i++) counts[i] = frequencyAtBin(i);
        return ClientSideHistogram.of(counts);
    }

    void mergeWith_(SingleDIDTimeToSyncHistogram that)
    {
        mergeSubThresholdWith_(that._subthreshold);
        mergeOverThresholdWith_(that._overthreshold);
    }

    private void mergeSubThresholdWith_(int [] thatSubthreshold)
    {
        checkArgument(thatSubthreshold.length == this._subthreshold.length);

        for (int i = 0; i < _subthreshold.length; i++) {
            _subthreshold[i] = _subthreshold[i] + thatSubthreshold[i];
        }
    }

    private void mergeOverThresholdWith_(List<Map<OID, Integer>> thoseOverThresholds)
    {
        checkArgument(thoseOverThresholds.size() == this._overthreshold.size());

        // Iterate over each of the bins over threshold for this and that object,
        // iterating on the same bin.
        Iterator<Map<OID, Integer>> iThese = _overthreshold.iterator();
        Iterator<Map<OID, Integer>> iThose = thoseOverThresholds.iterator();
        while (iThese.hasNext() && iThose.hasNext()) {
            Map<OID, Integer> thisBin = iThese.next(), thatBin = iThose.next();

            for (OID oid: thatBin.keySet()) {
                Integer countForOIDAtThisBin = thisBin.get(oid);
                if (countForOIDAtThisBin == null) countForOIDAtThisBin = 0;
                thisBin.put(oid, countForOIDAtThisBin + thatBin.get(oid));
            }
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

    public int size()
    {
        return _subthreshold.length + _overthreshold.size();
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("_sub", Arrays.toString(_subthreshold))
                .add("_over", _overthreshold)
                .toString();
    }
}
