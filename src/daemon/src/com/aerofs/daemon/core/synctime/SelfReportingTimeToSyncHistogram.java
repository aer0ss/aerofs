/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.rocklog.RockLog;
import com.aerofs.lib.sched.Scheduler;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkState;

class SelfReportingTimeToSyncHistogram extends AbstractEBSelfHandling
        implements TimeToSyncHistogram
{
    private static final Logger l = Loggers.getLogger(SelfReportingTimeToSyncHistogram.class);

    private final Map<DID, SingleDIDTimeToSyncHistogram> _histograms;
    private final Scheduler _sched;
    private final HistogramSender _histogramSender;
    private final RockLog _rockLog;

    private boolean _isScheduled = false;


    @Inject
    SelfReportingTimeToSyncHistogram(CoreScheduler sched, RockLog rockLog)
    {
        _rockLog = rockLog;
        _sched = sched;
        _histograms = Maps.newHashMap();
        _histogramSender = new HistogramSender(_sched, _histograms);
    }

    /**
     * For test with mock HistogramSender (otherwise _histogramSender will have a different Map
     * than _histograms)
     */
    @VisibleForTesting
    SelfReportingTimeToSyncHistogram(CoreScheduler sched, RockLog rockLog,
            HistogramSender histogramSender)
    {
        _rockLog = rockLog;
        _sched = sched;
        _histograms = Maps.newHashMap();
        _histogramSender = histogramSender;
    }

    @Override
    public void update_(DID did, OID oid, TimeToSync sync)
    {
        l.debug("update {} {} {}", did, oid, sync);
        SingleDIDTimeToSyncHistogram histogram = _histograms.get(did);
        if (histogram == null) {
            histogram = new SingleDIDTimeToSyncHistogram();
            _histograms.put(did, histogram);
        }

        histogram.update_(oid, sync);

        if (!_isScheduled) schedule_();
    }

    @Override
    public void handle_()
    {
        _isScheduled = false;

        // Send the current state of the histograms to the server if there is anything to report
        if (!_histograms.isEmpty()) {
            sendHistogramsToServerThenReset();
            checkState(_histograms.isEmpty(), _histograms + " should be empty");
        }
        schedule_();
    }

    private void sendHistogramsToServerThenReset()
    {
        l.info("send and reset");
        try {
            sendHistogramsToServerThenResetThrows();
        } catch (Exception ex) {
            l.warn("failed to send histogram for devices {}", _histograms.keySet());
            _rockLog.newDefect("daemon.synctime.sendHistograms")
                    .setException(ex)
                    .send();
        }
    }

    private void sendHistogramsToServerThenResetThrows()
    {
        Iterator<Entry<DID, SingleDIDTimeToSyncHistogram>> it = _histograms.entrySet().iterator();

        while (it.hasNext()) {
            Entry<DID, SingleDIDTimeToSyncHistogram> e = it.next();

            // Send the histogram, then remove it for this DID
            // N.B.
            // 1) if the download failed, the histogram will be merged back into {@link _histograms}
            // 2) remove the histogram during the loop because sending to the server could throw
            //    an exception, and successfully sent histograms should be removed, despite
            //    what might go wrong for histograms sent later
            _histogramSender.sendAsync(e.getKey(), e.getValue());

            // TODO (MJ) Current Problems:
            //  * the current Histogram does not collect OIDs, but when it does, note that
            //    clearing the histogram only on success will risk unbounded memory consumption.
            it.remove();
            l.debug("rm k {} from map", e.getKey());
        }
    }

    private void schedule_()
    {
        _sched.schedule(this, Params.SEND_HISTOGRAM_INTERVAL);
        _isScheduled = true;
    }
}
