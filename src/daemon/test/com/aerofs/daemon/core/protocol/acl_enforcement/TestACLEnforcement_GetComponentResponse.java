/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.acl_enforcement;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.protocol.CoreProtocolUtil;
import com.aerofs.daemon.core.protocol.ExSenderHasNoPerm;
import com.aerofs.daemon.core.protocol.MetaDiff;
import com.aerofs.daemon.core.protocol.class_under_test.GetComponentRequestWithMocks;
import com.aerofs.daemon.core.protocol.class_under_test.GetComponentResponseWithMocks;
import com.aerofs.daemon.core.transfers.download.IDownloadContext;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBGetComponentRequest;
import com.aerofs.proto.Core.PBMeta;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.Maps;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.util.SortedMap;

import static com.aerofs.daemon.core.protocol.ProtocolTestUtil.newContentSOCKID;
import static com.aerofs.daemon.core.protocol.ProtocolTestUtil.newDigestedMessage;
import static com.aerofs.daemon.core.protocol.ProtocolTestUtil.newMetadataSOCKID;
import static com.aerofs.daemon.core.protocol.class_under_test.AbstractClassUnderTestWithMocks.SINDEXES;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * See acl.md for definitions of ACL enforcement rules.
 */
public class TestACLEnforcement_GetComponentResponse extends AbstractTest
{
    GetComponentRequestWithMocks replier = new GetComponentRequestWithMocks();
    GetComponentResponseWithMocks caller = new GetComponentResponseWithMocks();

    SIndex _sidxViewer = SINDEXES[0];

    // The version of the component to be sent over the wire
    Version _v = Version.of(DID.generate(), new Tick(10));

    @Before
    public void setup()
            throws SQLException
    {
        // Minimum wiring to get things working
        when(replier._ds.isPresent_(any(SOCKID.class))).thenReturn(true);

        // Set up ACL. ACL checking on the replier side is passthrough.
        when(caller._lacl.check_(replier.user(), _sidxViewer, Permissions.EDITOR)).thenReturn(false);

        when(replier._ps.newFile_(any(ResolvedPath.class), any(KIndex.class))).then(RETURNS_DEEP_STUBS);
    }

    @Test
    public void caller_shouldEnforceRule2OnMetadata()
            throws Exception
    {
        final SOCKID k = newMetadataSOCKID(_sidxViewer);

        // Minimum wiring to get things working
        OA oa = OA.createNonFile(k.soid(), OID.generate(), "haha", OA.Type.ANCHOR, 0, null);
        when(replier._ds.getAliasedOANullable_(k.soid())).thenReturn(oa);
        SOID soidParent = new SOID(k.sidx(), oa.parent());
        when(caller._a2t.dereferenceAliasedOID_(soidParent)).thenReturn(soidParent);
        when(caller._mdiff.computeMetaDiff_(any(SOID.class), any(PBMeta.class), any(OID.class)))
                .thenReturn(MetaDiff.NAME | MetaDiff.PARENT);

        // connect replier to the caller
        doAnswer(invocation -> {
            PBCore pb = (PBCore)invocation.getArguments()[1];
            caller._gcr.processResponse_(k.socid(), newDigestedMessage(replier.user(), pb),
                    mock(IDownloadContext.class));
            return null;
        }).when(replier._trl).sendUnicast_(any(Endpoint.class), any(PBCore.class));

        run(k);

        verifyCallerWritesNothingToDB();
    }

    // See package-info.java for information about *_negative tests
    @Test(expected = Throwable.class)
    public void caller_shouldEnforceRule2OnMetadata_negative()
            throws Exception
    {
        setPassthroughLocalACL();

        caller_shouldEnforceRule2OnMetadata();
    }

    @Test
    public void caller_shouldEnforceRule2OnContent()
            throws Exception
    {
        final SOCKID k = newContentSOCKID(_sidxViewer);

        // Minimum wiring to get things working
        CA ca = mock(CA.class);
        SortedMap <KIndex, CA> cas = Maps.newTreeMap();
        cas.put(k.kidx(), ca);
        OA oa = mock(OA.class);
        when(oa.type()).thenReturn(OA.Type.FILE);
        when(oa.isFile()).thenReturn(true);
        when(oa.name()).thenReturn("haha");
        when(oa.soid()).thenReturn(k.soid());
        when(oa.parent()).thenReturn(OID.generate());
        when(oa.flags()).thenReturn(0);
        when(oa.casNoExpulsionCheck()).thenReturn(cas);
        when(oa.fid()).thenReturn(new FID(UniqueID.generate().getBytes()));
        when(replier._ds.getOA_(k.soid())).thenReturn(oa);

        // connect replier to the caller
        doAnswer(invocation -> {
            ByteArrayOutputStream os = (ByteArrayOutputStream)invocation.getArguments()[3];
            caller._gcr.processResponse_(k.socid(), newDigestedMessage(replier.user(), os),
                    mock(IDownloadContext.class));
            return null;
        }).when(replier._trl).sendUnicast_(any(Endpoint.class), anyString(), anyInt(),
                any(ByteArrayOutputStream.class));

        run(k);

        verifyCallerWritesNothingToDB();
    }

    // See package-info.java for information about *_negative tests
    @Test(expected = Throwable.class)
    public void caller_shouldEnforceRule2OnContent_negative()
            throws Exception
    {
        setPassthroughLocalACL();

        caller_shouldEnforceRule2OnContent();
    }

    private void setPassthroughLocalACL()
            throws SQLException
    {
        when(caller._lacl.check_(any(UserID.class), any(SIndex.class), any(Permissions.class)))
                .thenReturn(true);
    }

    private void run(SOCKID k)
            throws Exception
    {
        // compose a GetComCall for gcc.sendResponse_() to consume when sending the reply.
        PBCore request = CoreProtocolUtil
                .newRequest(Type.GET_COMPONENT_REQUEST)
                .setGetComponentRequest(PBGetComponentRequest
                        .newBuilder()
                        .setStoreId(replier._sidx2sid.getThrows_(k.sidx()).toPB())
                        .setObjectId(k.oid().toPB())
                        .setComId(k.cid().getInt())
                        .setLocalVersion(Version.empty().toPB_()))
                .build();

        try {
            replier._gcc.sendResponse_(newDigestedMessage(caller.user(), request), k, _v);
            fail();
        } catch (ExSenderHasNoPerm ignored) {}
    }

    private void verifyCallerWritesNothingToDB()
    {
        verifyNoMoreInteractions(caller._tm);
    }
}
