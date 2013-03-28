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
import com.aerofs.synctime.api.ClientSideHistogram;
import com.aerofs.synctime.client.TimeToSyncClient;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.Map.Entry;

class SelfReportingTimeToSyncHistogram extends AbstractEBSelfHandling
        implements TimeToSyncHistogram
{
    private Map<DID, TimeToSyncHistogramSingleDID> _histograms;

    private final Scheduler _sched;
    private boolean _isScheduled = false;
    private final TimeToSyncClient _ttsClient;

    private final RockLog _rockLog;

    private static final Logger l = Loggers.getLogger(SelfReportingTimeToSyncHistogram.class);

    @Inject
    public SelfReportingTimeToSyncHistogram(CoreScheduler sched, RockLog rockLog)
            throws URISyntaxException
    {
        this(sched, new TimeToSyncClient(Params.SERVER_URL.get().toURI()), rockLog);
    }

    SelfReportingTimeToSyncHistogram(CoreScheduler sched, TimeToSyncClient ttsClient,
            RockLog rockLog)
    {
        _rockLog = rockLog;
        _histograms = Maps.newHashMap();
        _sched = sched;
        _ttsClient = ttsClient;
    }

    @Override
    public void update_(DID did, OID oid, TimeToSync sync)
    {
        l.debug("update {} {} {}", did, oid, sync);
        TimeToSyncHistogramSingleDID histogram = _histograms.get(did);
        if (histogram == null) {
            histogram = new TimeToSyncHistogramSingleDID();
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
            l.info("send and reset");
            sendHistogramsToServerAndCatchExceptions();

            // Reset the histogram (GC will clean up the old histogram data once the histograms
            // are sent) TODO (MJ) will there be a sufficiently fast response from ^
            _histograms = Maps.newHashMap();
        }

        schedule_();
    }

    private void sendHistogramsToServerAndCatchExceptions()
    {
        try {
            sendHistogramsToServer();
        } catch (Exception ex) {
            l.warn("failed to send histogram for devices {}", _histograms.keySet());
            _rockLog.newDefect("daemon.synctime.sendHistograms")
                    .setException(ex)
                    .sendAsync();
        }
    }

    private void sendHistogramsToServer()
    {
        for (Entry<DID, TimeToSyncHistogramSingleDID> e : _histograms.entrySet()) {
            DID did = e.getKey();
            ClientSideHistogram histogram = e.getValue().toJSONableHistogram();
            // The following can throw all kinds of runtime exceptions.
            _ttsClient.sendHistogramForDevice(did.toStringFormal(), histogram);
        }
    }

    private void schedule_()
    {
        _sched.schedule(this, Params.SEND_HISTOGRAM_INTERVAL);
        _isScheduled = true;
    }
}
