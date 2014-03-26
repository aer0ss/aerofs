package com.aerofs.daemon.core.protocol;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBGetVersCallBlock;
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
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.PulledDeviceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Version;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.base.id.UniqueID;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.testlib.AbstractTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class TestGetVersCall extends AbstractTest
{
    private final InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();

    @Mock NativeVersionControl nvc;
    @Mock ImmigrantVersionControl ivc;
    @Mock TransportRoutingLayer trl;
    @Mock GetVersReply gvr;
    @Mock Metrics m;
    @Mock OutgoingStreams oss;
    @Mock MapSIndex2Store sidx2s;
    @Mock IMapSIndex2SID sidx2sid;
    @Spy  IPulledDeviceDatabase pulledDevices = new PulledDeviceDatabase(dbcw.getCoreDBCW(),
            mock(StoreDeletionOperators.class));

    // System Under Test
    @InjectMocks private GetVersCall gvc;

    @Captor private ArgumentCaptor<ByteArrayOutputStream> callCaptor;
    @Mock private Token tk;
    @Mock private Trans t;

    SIndex sidx = new SIndex(12345);
    SID sid = SID.generate();

    @Before
    public void setup() throws Exception
    {
        dbcw.init_();
        when(sidx2sid.get_(sidx)).thenReturn(sid);
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
        pulledDevices.insert_(sidx, didTo, t);

        // fromBase flag should be false
        assertFalse(getFlagFromBaseWhenRunningRPC(didTo));
    }

    /**
     * For the unit tests that are agnostic to the local knowledge vector,
     * return an empty version vector on knowledge requests.
     */
    private void mockEmptyKnowledge() throws Exception
    {
        when(nvc.getKnowledgeExcludeSelf_(sidx)).thenReturn(Version.empty());
        when(ivc.getKnowledgeExcludeSelf_(sidx)).thenReturn(Version.empty());
    }

    /**
     * Run the GetVersCall rpc method and extract the Protobuf from_base flag
     * @return whether from_base was set to true or false before the call to do_
     */
    private boolean getFlagFromBaseWhenRunningRPC(DID didTo) throws Exception
    {
        gvc.request_(sidx, didTo);

        verify(trl).sendUnicast_(eq(didTo), eq(Integer.toString(Type.GET_VERS_REQ.getNumber())),
                eq(CoreUtil.NOT_RPC), callCaptor.capture());

        ByteArrayInputStream is = new ByteArrayInputStream(callCaptor.getValue().toByteArray());
        PBCore.parseDelimitedFrom(is);
        return PBGetVersCallBlock.parseDelimitedFrom(is).getFromBase();
    }
}
