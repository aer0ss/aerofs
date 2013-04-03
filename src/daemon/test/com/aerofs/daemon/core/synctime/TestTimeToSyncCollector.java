/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.collector.RemoteUpdates;
import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.protocol.Download.Factory;
import com.aerofs.daemon.core.protocol.GetVersReply;
import com.aerofs.daemon.core.protocol.NewUpdates;
import com.aerofs.daemon.core.synctime.TimeToSyncCollector.TimeRetriever;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestTimeToSyncCollector extends AbstractTest
{
    @Mock TimeToSyncHistogram histogram;
    @Mock TimeRetriever time;
    @Mock RemoteUpdates ru;

    @Mock DevicePresence dp;
    @Mock NewUpdates nu;
    @Mock GetVersReply gvr;
    @Mock Factory dFact;

    @InjectMocks TimeToSyncCollector ttsc;

    private DID did = DID.generate();
    private SOCID socid = new SOCID(new SIndex(7), OID.generate(), CID.META);

    @Before
    public void setup() throws Exception
    {
        // For each timing event, we increment the current time by 1, to verify correct time diffs
        // in the tests.
        // TODO too bad I had to enter this consecutive sequence by hand.
        when(time.currentTimeMillis()).thenReturn(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);

        // Simulate by default that there are no known updates on any remote device
        when(ru.deviceHasUpdates_(any(DID.class))).thenReturn(false);

        // Simulate did coming online (used by every test)
        ttsc.deviceOnline_(did);
    }

    @Test
    public void shouldBeWiredToAllDependentNotifiers()
    {
        verify(dp).addListener_(ttsc);
        verify(nu).addListener_(ttsc);
        verify(gvr).addListener_(ttsc);
        verify(dFact).addListener_(ttsc);
    }

    @Test
    public void shouldNotCrashWhenDeviceGoesOnlineThenOffline()
    {
        ttsc.deviceOffline_(did);
    }

    @Test
    public void shouldHaveTimeDiffOfOneWhenDeviceOnlineFollowedByDownload()
    {
        ttsc.onPartialDownloadSuccess_(socid, did);

        verifyHistogramUpdatedWith(1L);
    }

    @Test
    public void shouldHaveTimeDiffOfOneWhenReceivedPushUpdateFollowedByDownload()
    {
        ttsc.receivedPushUpdate_(socid, did);
        ttsc.onPartialDownloadSuccess_(socid, did);

        verifyHistogramUpdatedWith(1L);
    }

    @Test
    public void shouldHaveDiffOfThreeWhenReceivedPushThenConsecutiveDownloads()
    {
        ttsc.receivedPushUpdate_(socid, did);
        ttsc.onPartialDownloadSuccess_(socid, did);
        ttsc.onPartialDownloadSuccess_(socid, did);

        // There have been 3 events since did came online, and the update timer for socid
        // should have been deleted by the first download. We'll attribute the second download to
        // when did came online.
        // TODO perhaps this needs rethinking, since the second download must have been caused by
        // a push update that was lost, or a pull update. Oh well.
        verifyHistogramUpdatedWith(3L);
    }

    @Test
    public void shouldHaveTimeDiffOfOneWhenReceivePushThenPullAndThereAreNoUpdatesOndid()
            throws Exception
    {
        ttsc.receivedPushUpdate_(socid, did);

        // Associate at least one time-stamp increment with the pull update
        time.currentTimeMillis();
        ttsc.receivedPullUpdateFrom_(did);

        ttsc.onPartialDownloadSuccess_(socid, did);

        // The pull update reset did's checkpoint, since there are no filters
        verifyHistogramUpdatedWith(1L);
    }


    @Test
    public void shouldHaveTimeDiffOfOneWhenReceivePullThenPushAndThereAreNoUpdatesOndid()
            throws Exception
    {
        // Associate at least one time-stamp increment with the pull update
        time.currentTimeMillis();
        ttsc.receivedPullUpdateFrom_(did);
        ttsc.receivedPushUpdate_(socid, did);

        ttsc.onPartialDownloadSuccess_(socid, did);

        verifyHistogramUpdatedWith(1L);
    }

    @Test
    public void shouldHaveTimeDiffOfTwoWhenReceivePushThenPullWithUpdatesOndid()
            throws Exception
    {
        ttsc.receivedPushUpdate_(socid, did);

        // The pull request below came with some Bloom filters (or some were left over from before)
        when(ru.deviceHasUpdates_(did)).thenReturn(true);
        // Associate at least one time-stamp increment with the pull update
        time.currentTimeMillis();
        ttsc.receivedPullUpdateFrom_(did);

        ttsc.onPartialDownloadSuccess_(socid, did);

        // The pull request did not set a new checkpoint (as filters existed for did),
        // so the last recorded time for (socid, did) is the receivedPushUpdate.
        verifyHistogramUpdatedWith(2L);
    }

    @Test
    public void shouldHandleDifferentComponentIDsSeparately()
    {
        SOCID socid2 = new SOCID(socid.soid(), CID.CONTENT);

        ttsc.receivedPushUpdate_(socid2, did);
        ttsc.receivedPushUpdate_(socid, did);
        ttsc.onPartialDownloadSuccess_(socid, did);
        ttsc.onPartialDownloadSuccess_(socid2, did);

        // socid was downloaded immediately (1 step) after the update was received
        // socid2 was downloaded 3 steps after its update was received
        verifyHistogramUpdatedWith(1L);
        verifyHistogramUpdatedWith(3L);
    }

    @Test
    public void shouldDistinguishBetweenPushUpdatesOfTwoDevicesForTheSameObject()
    {
        DID did2 = DID.generate();
        ttsc.deviceOnline_(did2);

        ttsc.receivedPushUpdate_(socid, did);
        ttsc.receivedPushUpdate_(socid, did2);
        ttsc.onPartialDownloadSuccess_(socid, did2);
        ttsc.onPartialDownloadSuccess_(socid, did);

        verify(histogram).update_(did2, socid.oid(), new TimeToSync(1L));
        verify(histogram).update_(did, socid.oid(), new TimeToSync(3L));
    }

    private void verifyHistogramUpdatedWith(long diff)
    {
        verify(histogram).update_(did, socid.oid(), new TimeToSync(diff));
    }
}
