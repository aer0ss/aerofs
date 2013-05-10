/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.synctime.api.ClientSideHistogram;
import com.aerofs.synctime.client.TimeToSyncClient;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * This class wraps the TimeToSyncClient to asynchronously send TTS histograms to the server.
 * It ensures that either
 *  i)  the POST succeeded, or
 *  ii) the histogram that failed to send was merged back into the map of histograms with
 *      which the class was constructed
 *
 *  N.B.
 *  1) This is not injected with Guice because the Map of DID->SingleDIDTimeToSyncHistogram
 *     is hard to inject. Perhaps that is a sign that there should be a MultiDIDTimeToSyncHistogram
 *     that exposes mergeWith() and consumingIterator() methods. However I liked the idea that
 *     the SelfReportingTimeToSyncHistogram "knows" that it removes entries after sending to the
 *     server. A MultiDIDTimeToSyncHistogram would let other objects remove its contents by exposing
 *     an iterator; that seems cowardly.
 *  2) The class uses guava's JdkFutureAdapters thread pool. If other objects in the Core need to
 *     use their own thread pool, the Core should have one Executor to manage these Future-waiting
 *     threads. Because of point 1, it will be tough to @Inject such a global Executor.
 *  3) Much of this code is tough to test, but is fairly simple. Be careful.
 */
class HistogramSender
{
    private static final Logger l = Loggers.getLogger(HistogramSender.class);
    private final TimeToSyncClient _ttsClient;
    private final Scheduler _sched;
    private final Map<DID, SingleDIDTimeToSyncHistogram> _histograms;

    HistogramSender(Scheduler sched, Map<DID, SingleDIDTimeToSyncHistogram> histograms)
    {
        _ttsClient = new TimeToSyncClient(URI.create(Params.SERVER_URL.get()));
        _sched = sched;
        _histograms = histograms;
    }

    void sendAsync(DID did, SingleDIDTimeToSyncHistogram histogram)
    {
        ClientSideHistogram jsonableHistogram = histogram.toJSONableHistogram();

        // The following can throw all kinds of runtime exceptions.
        Future<?> f = _ttsClient.sendHistogramForDevice(did.toStringFormal(), jsonableHistogram);

        // TODO (MJ) add a daemon-wide Futures pool (Executor)
        ListenableFuture<?> listenableFuture = JdkFutureAdapters.listenInPoolThread(f);

        Futures.addCallback(listenableFuture, new PostHistogramSendingCallback(did, histogram));
    }

    private class PostHistogramSendingCallback implements FutureCallback<Object>
    {
        private final DID _did;
        private final SingleDIDTimeToSyncHistogram _histogram;

        private PostHistogramSendingCallback(DID did, SingleDIDTimeToSyncHistogram histogram)
        {
            _did = did;
            _histogram = histogram;
        }

        @Override
        public void onSuccess(Object o)
        {
            // No-op; the histogram should have already been removed from the map,
            // or replaced with a new histogram.
            l.debug("histogram for {} sent successfully", _did);
        }

        @Override
        public void onFailure(Throwable throwable)
        {
            // Since the histogram failed to send, merge it with any new histogram for did.
            // Schedule a Core event to perform the merging since the core lock must be held.
            _sched.schedule(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    l.debug("merge histogram for d {}", _did);
                    if (_histograms.containsKey(_did)) {
                        _histograms.get(_did).mergeWith_(_histogram);
                    } else {
                        _histograms.put(_did, _histogram);
                    }
                }
            }, 0);
        }
    }

}
