/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.daemon.core.lib.BaseDownloadsTest;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.ids.DID;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class TestDownloads extends BaseDownloadsTest
{
    private @Mock AsyncDownload.Factory daemonFactDl;
    private @Mock AsyncDownload dl;
    private SIndex sidx = new SIndex(1);

    @Before
    public void setUp() throws Exception
    {
        factDL = daemonFactDl;
        Downloads dlsImpl = new Downloads();
        dlsImpl.inject_(sched, tokenManager, (AsyncDownload.Factory)factDL, mock(CfgUsePolaris.class));
        dls = dlsImpl;
        when(daemonFactDl.create_(any(SOCID.class), anySetOf(DID.class),
                any(IDownloadCompletionListener.class), any(Token.class))).thenReturn((dl));
        when(cedb.getChangeEpoch_(any(SIndex.class))).thenReturn(null);
    }

    @Test
    public void shouldNotStartDownloadIfNoTokensAvailable() throws Exception
    {
        super.shouldNotStartDownloadIfNoTokensAvailable();
    }

    @Test
    public void shouldStartDownloadImmediately() throws Exception
    {
        super.shouldStartDownloadImmediately();
        // TODO(AS): Remove and refactor once fully transitioned to polaris..
        // Due to differences in legacy world and polaris world; the AsyncDownload
        // Factory in the old world needs to be aware of the component its downloading from other
        // peers while in polaris world it doesn't. This leads to different param list for the
        // create function.
        verify(daemonFactDl).create_(eq(new SOCID(soid, CID.CONTENT)), eq(ImmutableSet.of(did)),
                eq(dcl), Matchers.eq(tk));
    }

    @Test
    public void shouldIncludeSameDeviceInOngoingDownload() throws Exception
    {
        super.shouldIncludeSameDeviceInOngoingDownload();
        verify(daemonFactDl).create_(eq(new SOCID(soid, CID.CONTENT)),
                eq(ImmutableSet.of(did)), eq(dcl),Matchers.eq(tk));
        verifyNoMoreInteractions(factDL);
        verify(dl).include_(anySetOf(DID.class), eq(dcl));
        Mockito.verifyNoMoreInteractions(tokenManager);
    }

    @Test
    public void shouldIncludeDifferentDeviceInOngoingDownload() throws Exception
    {
        super.shouldIncludeDifferentDeviceInOngoingDownload();
        verify(daemonFactDl).create_(eq(new SOCID(soid, CID.CONTENT)),
                eq(ImmutableSet.of(did)), eq(dcl), Matchers.eq(tk));
        verifyNoMoreInteractions(factDL);
        verify(dl).include_(anySetOf(DID.class), eq(dcl));
        Mockito.verifyNoMoreInteractions(tokenManager);
    }

    // TODO: test synchronous download logic
}
