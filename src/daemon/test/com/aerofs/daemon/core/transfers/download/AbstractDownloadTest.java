/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.protocol.GetContentRequest;
import com.aerofs.daemon.core.protocol.GetContentResponse;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.protocol.GetComponentRequest;
import com.aerofs.daemon.core.protocol.GetComponentResponse;
import com.aerofs.daemon.core.transfers.download.AsyncDownload.RemoteChangeChecker;
import com.aerofs.daemon.core.transfers.download.dependence.DownloadDeadlockResolver;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.Maps;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.stubbing.OngoingStubbing;

import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractDownloadTest extends AbstractTest
{
    @Mock To to;
    @Mock Token tk;
    @Mock IDownloadCompletionListener dcl;
    @Mock ITransport tp;

    @Mock DirectoryService ds;
    @Mock DownloadState dlstate;
    @Mock Downloads dls;
    @Mock GetComponentRequest gcc;
    @Mock GetComponentResponse gcr;
    @Mock ChangeEpochDatabase cedb;
    @Mock GetContentRequest pgcc;
    @Mock GetContentResponse pgcr;
    @Mock RemoteChangeChecker changes;
    @Mock DownloadDeadlockResolver ddr;
    @Mock IMapSIndex2SID sidx2sid;

    @Mock protected CfgLocalDID cfgLocalDID;

    @Mock To.Factory factTo;
    /*@InjectMocks*/ AsyncDownload.Factory factDL;

    AsyncDownload asyncdl(SOCID socid)
    {
        return new AsyncDownload(factDL, socid, to, dcl, tk);
    }


    Download dl(SOCID socid)
    {
        Download dl = new Download(factDL, socid, to, tk);
        dl._cxt = dl.new Cxt();
        return dl;
    }

    final SIndex sidx = new SIndex(1);
    final SID sid = SID.generate();

    final DID did1 = DID.generate();
    final DID did2 = DID.generate();
    final DID did3 = DID.generate();

    @Before
    public void baseSetUp() throws Exception
    {
        AppRoot.set("dummy");

        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);
        when(cedb.getChangeEpoch_(any(SIndex.class))).thenReturn(null);
        when(cfgLocalDID.get()).thenReturn(new DID(UniqueID.generate()));

        factTo = new To.Factory(mock(Devices.class), cfgLocalDID);
        factDL = new AsyncDownload.Factory(ds, dlstate, dls, gcc, gcr, factTo, ddr, sidx2sid, changes,
                cedb, pgcc, pgcr);
    }


    void mockReplies(SOCID socid, DID... dids) throws Exception
    {
        mockDeviceSelection(dids);
        mockGCC(socid, dids);
    }

    private void mockDeviceSelection(DID... dids) throws Exception
    {
        OngoingStubbing<DID> stubTo =
                when(to.pick_());
        for (DID d : dids) stubTo = stubTo.thenReturn(d);
        stubTo.thenThrow(new ExNoAvailDevice());
    }

    private void mockGCC(SOCID socid, DID... dids) throws Exception
    {
        // sigh, cannot stub replies while stubbing gcc...
        Map<DID, DigestedMessage> replies = Maps.newHashMap();
        for (DID d : dids) replies.put(d, mockReply(d));

        OngoingStubbing<DigestedMessage> stubGCC =
                when(gcc.remoteRequestComponent_(eq(socid), any(DID.class), eq(tk)));
        for (DID d : dids) stubGCC = stubGCC.thenReturn(replies.get(d));
    }

    private DigestedMessage mockReply(DID replier) throws Exception
    {
        DigestedMessage msg = mock(DigestedMessage.class);
        when(msg.did()).thenReturn(replier);
        when(msg.tp()).thenReturn(tp);
        when(msg.ep()).thenReturn(new Endpoint(tp, replier));
        return msg;
    }

    static DigestedMessage anyDM() { return any(DigestedMessage.class); }
    static IDownloadContext anyDC() { return any(IDownloadContext.class); }

    static Endpoint endpoint(final DID did)
    {
        return argThat(new BaseMatcher<Endpoint>()
        {
            @Override
            public boolean matches(Object o)
            {
                return did.equals(((Endpoint)o).did());
            }

            @Override
            public void describeTo(Description description)
            {
                description.appendText("ep(" + did + ")");
            }
        });
    }
}
