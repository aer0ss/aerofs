package com.aerofs.daemon.core.lib;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.ITokenReclamationListener;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.core.transfers.download.IAsyncDownloadFactory;
import com.aerofs.daemon.core.transfers.download.IContentDownloads;
import com.aerofs.daemon.core.transfers.download.IDownloadCompletionListener;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BaseDownloadsTest extends AbstractTest
{
    protected @Mock Token tk;
    protected @Mock CoreScheduler sched;
    protected @Mock TokenManager tokenManager;

    protected @Mock ITokenReclamationListener trl;
    protected @Mock IDownloadCompletionListener dcl;
    protected @Mock ChangeEpochDatabase cedb;
    protected @Mock IAsyncDownloadFactory factDL;

    protected IContentDownloads dls;
    protected DID did = DID.generate();
    protected SIndex sidx = new SIndex(1);
    protected SOID soid = new SOID(sidx, OID.generate());


    protected void shouldNotStartDownloadIfNoTokensAvailable() throws Exception
    {
        when(tokenManager.acquire_(eq(Cat.CLIENT), anyString())).thenReturn(null);
        assertFalse(dls.downloadAsync_(new SOID(sidx, OID.generate()), ImmutableSet.of(did), trl, dcl));
        verify(tokenManager).acquire_(eq(Cat.CLIENT), anyString());
        verify(tokenManager).addTokenReclamationListener_(Cat.CLIENT, trl);
        Mockito.verifyNoMoreInteractions(tokenManager);
        Mockito.verifyZeroInteractions(sched, factDL);
    }

    protected void shouldStartDownloadImmediately() throws Exception
    {
        when(tokenManager.acquire_(eq(Cat.CLIENT), anyString())).thenReturn(tk);
        assertTrue(dls.downloadAsync_(soid, ImmutableSet.of(did), trl, dcl));
        verify(tokenManager).acquire_(eq(Cat.CLIENT), anyString());
        Mockito.verifyNoMoreInteractions(tokenManager);
        verify(sched).schedule_(any(IEvent.class));
        Mockito.verifyZeroInteractions(sched);
    }

    protected void shouldIncludeSameDeviceInOngoingDownload() throws Exception
    {
        when(tokenManager.acquire_(eq(Cat.CLIENT), anyString())).thenReturn(tk);
        assertTrue(dls.downloadAsync_(soid, ImmutableSet.of(did), trl, dcl));
        verify(tokenManager).acquire_(eq(Cat.CLIENT), anyString());
        assertTrue(dls.downloadAsync_(soid, ImmutableSet.of(did), trl, dcl));
    }

    protected void shouldIncludeDifferentDeviceInOngoingDownload() throws Exception
    {
        when(tokenManager.acquire_(eq(Cat.CLIENT), anyString())).thenReturn(tk);
        assertTrue(dls.downloadAsync_(soid, ImmutableSet.of(did), trl, dcl));
        verify(tokenManager).acquire_(eq(Cat.CLIENT), anyString());
        Set<DID> dids = ImmutableSet.of(DID.generate(), DID.generate());
        assertTrue(dls.downloadAsync_(soid, dids, trl, dcl));
    }
}
