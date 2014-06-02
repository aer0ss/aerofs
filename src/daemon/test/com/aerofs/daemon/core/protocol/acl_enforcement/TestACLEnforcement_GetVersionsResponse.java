/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.acl_enforcement;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.protocol.class_under_test.GetVersionsRequestWithMocks;
import com.aerofs.daemon.core.protocol.class_under_test.GetVersionsResponseWithMocks;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBGetVersionsResponse;
import com.aerofs.proto.Core.PBGetVersionsResponseBlock;
import com.aerofs.proto.Core.PBStoreHeader;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.daemon.core.protocol.ProtocolTestUtil.newDigestedMessage;
import static com.aerofs.daemon.core.protocol.class_under_test.AbstractClassUnderTestWithMocks.SIDS;
import static com.aerofs.daemon.core.protocol.class_under_test.AbstractClassUnderTestWithMocks.SINDEXES;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * See acl.md for definitions of ACL enforcement rules.
 */
public class TestACLEnforcement_GetVersionsResponse extends AbstractTest
{
    GetVersionsResponseWithMocks caller = new GetVersionsResponseWithMocks();
    GetVersionsRequestWithMocks replier = new GetVersionsRequestWithMocks();

    SIndex _sidxViewer = SINDEXES[0];
    SID _sidViewer = SIDS[0];

    @Before
    public void setup()
            throws SQLException
    {
        // Minimum wiring to get things working
        when(caller._nvc.getKnowledgeExcludeSelf_(any(SIndex.class))).thenReturn(Version.empty());
        when(caller._ivc.getKnowledgeExcludeSelf_(any(SIndex.class))).thenReturn(Version.empty());
    }

    @Test
    public void caller_shouldEnforceRule2()
            throws Exception
    {
        // Setup ACL checking for the caller.
        when(caller._lacl.check_(replier.user(), _sidxViewer, Permissions.EDITOR)).thenReturn(false);

        // ACL checking on the replier side is passthrough

        runAndVerify();
    }

    // See package-info.java for information about *_negative tests
    @Test(expected = Throwable.class)
    public void caller_shouldEnforceRule2_negative()
            throws Exception
    {
        runAndVerify();
    }

    private void runAndVerify()
            throws Exception
    {
        // Unlike other tests in the same package, for this test it is easier to craft a protobuf
        // reply than connecting the caller and the replier.
        caller._gvr.processResponse_(newDigestedMessage(replier.user(), newResponse()));

        // verify that the caller writes nothing to the db
        verifyNoMoreInteractions(caller._tm);
    }

    private ByteArrayOutputStream newResponse()
            throws IOException
    {
        PBCore core = PBCore.newBuilder()
                .setType(Type.REPLY)
                .setRpcid(1)
                .setGetVersionsResponse(PBGetVersionsResponse.newBuilder())
                .build();

        PBGetVersionsResponseBlock block = PBGetVersionsResponseBlock
                .newBuilder()
                .setStore(PBStoreHeader
                        .newBuilder()
                        .setStoreId(_sidViewer.toPB()))
                .setDeviceId(DID.generate().toPB())
                .addObjectId(OID.generate().toPB())
                .addComId(CID.CONTENT.getInt())
                .addTick(123)
                .setKnowledgeTick(123)
                .setIsLastBlock(true)
                .build();

        ByteArrayOutputStream os = Util.writeDelimited(core);
        block.writeDelimitedTo(os);
        return os;
    }
}
