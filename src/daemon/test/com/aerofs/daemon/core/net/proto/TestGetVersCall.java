package com.aerofs.daemon.core.net.proto;

import com.aerofs.daemon.core.store.IMapSIndex2Store;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.OutgoingStreams;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.PulledDeviceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Version;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.testlib.AbstractTest;

public class TestGetVersCall extends AbstractTest
{
    private final InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();

    @Mock NativeVersionControl nvc;
    @Mock ImmigrantVersionControl ivc;
    @Mock RPC rpc;
    @Mock GetVersReply gvr;
    @Mock Metrics m;
    @Mock OutgoingStreams oss;
    @Mock IMapSIndex2Store sidx2s;
    @Spy  IPulledDeviceDatabase pulledDevices = new PulledDeviceDatabase(dbcw.mockCoreDBCW());

    // System Under Test
    @InjectMocks private GetVersCall gvc;

    @Captor private ArgumentCaptor<PBCore> callCaptor;
    @Mock private Token tk;
    @Mock private Trans t;

    SIndex sidx = new SIndex(12345);

    @Before
    public void setup() throws Exception
    {
        dbcw.init_();
    }

    @After
    public void tearDown() throws Exception
    {
        dbcw.fini_();
    }

    @Test
    public void shouldLoadPBWithFromBaseTrueWhenSIndexAndDIDHaveNotBeenPulled()
            throws Exception
    {
        // The DID the rpc_ call is intended for
        DID didTo = new DID(UniqueID.generate());
        mockEmptyKnowledge();

        // Ensure that store s has never been pulled from didTo
        assertFalse(pulledDevices.contains_(sidx, didTo));

        // from_base flag should be true
        assertTrue(getFlagFromBaseWhenRunningRPC(didTo));
    }

    @Test
    public void shouldLoadPBWithFromBaseFalseWhenSIndexAndDIDHaveBeenPulled()
            throws Exception
    {
        // The DID the rpc_ call is intended for
        DID didTo = new DID(UniqueID.generate());
        mockEmptyKnowledge();

        // Mock that knowledge about store s has been pulled from didTo.
        pulledDevices.add_(sidx, didTo, t);

        // fromBase flag should be false
        assertFalse(getFlagFromBaseWhenRunningRPC(didTo));
    }

    /**
     * For the unit tests that are agnostic to the local knowledge vector,
     * return an empty version vector on knowledge requests.
     */
    private void mockEmptyKnowledge() throws Exception
    {
        when(nvc.getKnowledgeExcludeSelf_(sidx)).thenReturn(new Version());
        when(ivc.getKnowledgeExcludeSelf_(sidx)).thenReturn(new Version());
    }

    /**
     * Run the GetVersCall rpc method and extract the Protobuf from_base flag
     * @return whether from_base was set to true or false before the call to do_
     */
    private boolean getFlagFromBaseWhenRunningRPC(DID didTo) throws Exception
    {
        gvc.rpc_(sidx, didTo, tk);

        verify(rpc).do_(eq(didTo), any(SIndex.class), callCaptor.capture(), any(Token.class),
                any(String.class));

        return callCaptor.getValue().getGetVersCall().getFromBase();
    }
}
