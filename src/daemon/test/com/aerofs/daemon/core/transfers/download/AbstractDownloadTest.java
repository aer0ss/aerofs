/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.protocol.GetComponentRequest;
import com.aerofs.daemon.core.protocol.GetComponentResponse;
import com.aerofs.daemon.core.protocol.GetContentRequest;
import com.aerofs.daemon.core.protocol.GetContentResponse;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.transfers.download.AsyncDownload.RemoteChangeChecker;
import com.aerofs.daemon.core.transfers.download.dependence.DownloadDeadlockResolver;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.Maps;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.stubbing.OngoingStubbing;

import java.util.Map;

import static com.aerofs.daemon.core.lib.AsyncDownloadTestHelper.mockDeviceSelection;
import static com.aerofs.daemon.core.lib.AsyncDownloadTestHelper.mockReply;
import static org.mockito.Matchers.any;
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
    @Mock Downloads dls;
    @Mock GetComponentRequest gcc;
    @Mock GetComponentResponse gcr;
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
        when(cfgLocalDID.get()).thenReturn(new DID(UniqueID.generate()));

        factTo = new To.Factory(mock(Devices.class), cfgLocalDID);
        factDL = new AsyncDownload.Factory(ds, dls, gcc, gcr, factTo, ddr, sidx2sid, changes,
                mock(CfgUsePolaris.class), pgcc, pgcr);
    }


    void mockReplies(SOCID socid, DID... dids) throws Exception
    {
        mockDeviceSelection(to, dids);
        mockGCC(socid, dids);
    }

    private void mockGCC(SOCID socid, DID... dids) throws Exception
    {
        // sigh, cannot stub replies while stubbing gcc...
        Map<DID, DigestedMessage> replies = Maps.newHashMap();
        for (DID d : dids) replies.put(d, mockReply(d, tp));

        OngoingStubbing<DigestedMessage> stubGCC =
                when(gcc.remoteRequestComponent_(eq(socid), any(DID.class), eq(tk)));
        for (DID d : dids) stubGCC = stubGCC.thenReturn(replies.get(d));
    }

    static IDownloadContext anyDC() { return any(IDownloadContext.class); }

}
