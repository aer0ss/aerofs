/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.ITokenReclamationListener;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestDownloads extends AbstractTest
{
    @Mock Token tk;

    @Mock CoreQueue q;
    @Mock CoreScheduler sched;
    @Mock TokenManager tokenManager;

    @Mock AsyncDownload.Factory factDL;

    Downloads dls;

    SOCID gen(CID cid)
    {
        return new SOCID(sidx, OID.generate(), cid);
    }

    SIndex sidx = new SIndex(1);

    DID did = DID.generate();
    @Mock ITokenReclamationListener trl;
    @Mock IDownloadCompletionListener dcl;
    @Mock ChangeEpochDatabase cedb;

    @Before
    public void setUp() throws Exception
    {
        dls = new Downloads();
        dls.inject_(q, sched, tokenManager, factDL, cedb);
        when(cedb.getChangeEpoch_(any(SIndex.class))).thenReturn(null);
    }

    @Test
    public void shouldNotStartDownloadIfNoTokensAvailable() throws Exception
    {
        SOCID socid = gen(CID.META);
        when(tokenManager.acquire_(eq(Cat.CLIENT), anyString())).thenReturn(null);

        assertFalse(dls.downloadAsync_(socid, ImmutableSet.of(did), trl, dcl));

        verify(tokenManager).acquire_(eq(Cat.CLIENT), anyString());
        verify(tokenManager).addTokenReclamationListener_(Cat.CLIENT, trl);
        verifyNoMoreInteractions(tokenManager);

        verifyZeroInteractions(q, sched, factDL);
    }

    @Test
    public void shouldStartDownloadImmediately() throws Exception
    {
        SOCID socid = gen(CID.META);
        when(tokenManager.acquire_(eq(Cat.CLIENT), anyString())).thenReturn(tk);
        when(q.enqueue_(any(IEvent.class), any(Prio.class))).thenReturn(true);

        assertTrue(dls.downloadAsync_(socid, ImmutableSet.of(did), trl, dcl));

        verify(tokenManager).acquire_(eq(Cat.CLIENT), anyString());
        verifyNoMoreInteractions(tokenManager);

        verify(factDL).create_(eq(socid), eq(ImmutableSet.of(did)), eq(dcl), eq(tk));

        verify(q).enqueue_(any(IEvent.class), any(Prio.class));

        verifyZeroInteractions(sched);
    }

    @Test
    public void shouldScheduleDownloadIfCoreQueueFull() throws Exception
    {
        SOCID socid = gen(CID.META);
        when(tokenManager.acquire_(eq(Cat.CLIENT), anyString())).thenReturn(tk);
        when(q.enqueue_(any(IEvent.class), any(Prio.class))).thenReturn(false);

        assertTrue(dls.downloadAsync_(socid, ImmutableSet.of(did), trl, dcl));

        verify(tokenManager).acquire_(eq(Cat.CLIENT), anyString());
        verifyNoMoreInteractions(tokenManager);

        verify(factDL).create_(eq(socid), eq(ImmutableSet.of(did)), eq(dcl), eq(tk));

        verify(q).enqueue_(any(IEvent.class), any(Prio.class));
        verify(sched).schedule(any(IEvent.class), anyLong());
    }

    @Test
    public void shouldIncludeSameDeviceInOngoingDownload() throws Exception
    {
        SOCID socid = gen(CID.META);
        when(tokenManager.acquire_(eq(Cat.CLIENT), anyString())).thenReturn(tk);
        when(q.enqueue_(any(IEvent.class), any(Prio.class))).thenReturn(true);
        AsyncDownload dl = mock(AsyncDownload.class);
        when(factDL.create_(eq(socid), anySetOf(DID.class), eq(dcl), eq(tk))).thenReturn(dl);

        assertTrue(dls.downloadAsync_(socid, ImmutableSet.of(did), trl, dcl));

        verify(tokenManager).acquire_(eq(Cat.CLIENT), anyString());
        verify(factDL).create_(eq(socid), eq(ImmutableSet.of(did)), eq(dcl), eq(tk));

        assertTrue(dls.downloadAsync_(socid, ImmutableSet.of(did), trl, dcl));

        verify(dl).include_(eq(ImmutableSet.of(did)), eq(dcl));

        verifyNoMoreInteractions(factDL);
        verifyNoMoreInteractions(tokenManager);
    }

    @Test
    public void shouldIncludeDifferentDeviceInOngoingDownload() throws Exception
    {
        SOCID socid = gen(CID.META);
        when(tokenManager.acquire_(eq(Cat.CLIENT), anyString())).thenReturn(tk);
        when(q.enqueue_(any(IEvent.class), any(Prio.class))).thenReturn(true);
        AsyncDownload dl = mock(AsyncDownload.class);
        when(factDL.create_(eq(socid), anySetOf(DID.class), eq(dcl), eq(tk))).thenReturn(dl);

        assertTrue(dls.downloadAsync_(socid, ImmutableSet.of(did), trl, dcl));

        verify(tokenManager).acquire_(eq(Cat.CLIENT), anyString());
        verify(factDL).create_(eq(socid), eq(ImmutableSet.of(did)), eq(dcl), eq(tk));

        Set<DID> dids = ImmutableSet.of(DID.generate(), DID.generate());
        assertTrue(dls.downloadAsync_(socid, dids, trl, dcl));

        verify(dl).include_(eq(dids), eq(dcl));

        verifyNoMoreInteractions(factDL);
        verifyNoMoreInteractions(tokenManager);
    }

    // TODO: test synchronous download logic
}
