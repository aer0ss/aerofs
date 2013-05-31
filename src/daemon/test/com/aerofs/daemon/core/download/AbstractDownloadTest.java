/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.download;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.download.Download.Cxt;
import com.aerofs.daemon.core.download.dependence.DownloadDeadlockResolver;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.core.protocol.GetComponentCall;
import com.aerofs.daemon.core.protocol.GetComponentReply;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
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
    @Mock GetComponentCall gcc;
    @Mock GetComponentReply gcr;
    @Mock NativeVersionControl nvc;
    @Mock DownloadDeadlockResolver ddr;

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
    final DID did1 = DID.generate();
    final DID did2 = DID.generate();
    final DID did3 = DID.generate();

    @Before
    public void baseSetUp() throws Exception
    {
        AppRoot.set("dummy");

        factTo = new To.Factory(mock(DevicePresence.class), mock(MapSIndex2Store.class));
        factDL = new AsyncDownload.Factory(ds, dlstate, dls, gcc, gcr, factTo, nvc, ddr);
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
                when(gcc.remoteRequestComponent_(eq(socid), any(To.class), eq(tk)));
        for (DID d : dids) stubGCC = stubGCC.thenReturn(replies.get(d));
    }

    private DigestedMessage mockReply(DID replier) throws Exception
    {
        DigestedMessage msg = mock(DigestedMessage.class);
        when(msg.did()).thenReturn(replier);
        when(msg.tp()).thenReturn(tp);
        when(msg.sidx()).thenReturn(sidx);
        when(msg.ep()).thenReturn(new Endpoint(tp, replier));
        return msg;
    }

    static DigestedMessage anyDM() { return any(DigestedMessage.class); }
    static IDownloadContext anyDC() { return any(IDownloadContext.class); }

    static To from(final DID did)
    {
        return argThat(new BaseMatcher<To>()
        {
            @Override
            public boolean matches(Object o)
            {
                return ImmutableSet.of(did).equals(((To)o).dids());
            }

            @Override
            public void describeTo(Description description)
            {
                description.appendText("from(" + did + ")");
            }
        });
    }

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
