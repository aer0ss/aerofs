/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.protocol.ExRestartWithHashComputed;
import com.aerofs.daemon.core.transfers.download.dependence.DependencyEdge;
import com.aerofs.daemon.core.transfers.download.dependence.DependencyEdge.DependencyType;
import com.aerofs.daemon.lib.exception.ExDependsOn;
import com.aerofs.daemon.lib.exception.ExNameConflictDependsOn;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.OCID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBMeta;
import com.aerofs.proto.Core.PBMeta.Type;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Download contract:
 *   - tries to fetch an object from a given DID
 *   - tries to resolve dependencies
 */
public class TestDownload extends AbstractDownloadTest
{
    final SOID soid = new SOID(sidx, OID.generate());
    final SOCID socid = new SOCID(soid, CID.CONTENT);

    @Before
    public void setUp() throws Exception
    {
        when(ds.getAliasedOANullable_(soid))
                .thenReturn(mock(OA.class));
    }

    @Test
    public void shouldAbortIfStoreExpelled() throws Exception
    {
        when(ds.getAliasedOANullable_(soid)).thenReturn(null);
        when(sidx2sid.getNullable_(sidx)).thenReturn(null);

        try {
            dl(socid).download_();
            fail();
        } catch (ExAborted e) {}
    }

    @Test
    public void shouldAbortIfExpelled() throws Exception
    {
        OA oa = mock(OA.class);
        when(oa.isExpelled()).thenReturn(true);
        when(ds.getAliasedOANullable_(soid)).thenReturn(oa);

        try {
            dl(socid).download_();
            fail();
        } catch (ExAborted e) {}
    }

    @Test
    public void shouldSynchronouslyDownloadMETA() throws Exception
    {
        when(ds.getAliasedOANullable_(soid))
                .thenReturn(null)
                .thenReturn(mock(OA.class));

        Set<DID> dids = ImmutableSet.of(did1, did2);

        when(to.allDIDs()).thenReturn(dids);
        mockReplies(socid, did1);

        dl(socid).download_();

        InOrder ordered = inOrder(dls, dlstate);

        ordered.verify(dls).downloadSync_(eq(new SOCID(soid, CID.META)), eq(dids), anyDC());

        ordered.verify(dlstate).ended_(eq(socid), endpoint(did1), eq(false));
    }

    @Test
    public void shouldResolveParentDep() throws Exception
    {
        final SOCID child = new SOCID(sidx, OID.generate(), CID.META);
        final SOCID parent = new SOCID(sidx, OID.generate(), CID.META);

        mockReplies(child, did1, did1);
        mockReplies(parent, did1);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                doNothing().when(gcr).processReply_(eq(child), anyDM(), anyDC());
                throw new ExDependsOn(new OCID(parent.oid(), parent.cid()), DependencyType.PARENT);
            }
        }).when(gcr).processReply_(eq(child), anyDM(), anyDC());

        dl(child).download_();

        InOrder ordered = inOrder(gcc, dlstate);

        ordered.verify(gcc).remoteRequestComponent_(eq(child), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(child), endpoint(did1), eq(true));

        ordered.verify(gcc).remoteRequestComponent_(eq(parent), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(parent), endpoint(did1), eq(false));

        ordered.verify(gcc).remoteRequestComponent_(eq(child), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(child), endpoint(did1), eq(false));

        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldResolveNameConflictDep() throws Exception
    {
        final SOCID o1 = new SOCID(sidx, OID.generate(), CID.META);
        final SOCID o2 = new SOCID(sidx, OID.generate(), CID.META);
        final SOCID p = new SOCID(sidx, OID.generate(), CID.META);

        mockReplies(o1, did1);
        mockReplies(o2, did1, did1);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                throw new ExNameConflictDependsOn(o1.oid(), p.oid(), Version.of(did1, new Tick(42)),
                        PBMeta.newBuilder()
                                .setType(Type.FILE)
                                .setName("foo")
                                .setParentObjectId(p.oid().toPB())
                                .setFlags(0)
                                .build(),
                        o2.soid());
            }
        }).when(gcr).processReply_(eq(o2), anyDM(), anyDC());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                doNothing().when(gcr).processReply_(eq(o2), anyDM(), anyDC());
                return null;
            }
        }).when(gcr).processReply_(eq(o1), anyDM(), anyDC());

        dl(o2).download_();

        InOrder ordered = inOrder(gcc, dlstate);

        ordered.verify(gcc).remoteRequestComponent_(eq(o2), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(o2), endpoint(did1), eq(true));

        ordered.verify(gcc).remoteRequestComponent_(eq(o1), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(o1), endpoint(did1), eq(false));

        ordered.verify(gcc).remoteRequestComponent_(eq(o2), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(o2), endpoint(did1), eq(false));

        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldResolveNameConflictDepWhenLocalDominatesRemote() throws Exception
    {
        final SOCID o1 = new SOCID(sidx, OID.generate(), CID.META);
        final SOCID o2 = new SOCID(sidx, OID.generate(), CID.META);
        final SOCID p = new SOCID(sidx, OID.generate(), CID.META);

        mockReplies(o1, did1);
        mockReplies(o2, did1, did1);

        doThrow(new ExNameConflictDependsOn(o1.oid(), p.oid(), Version.of(did1, new Tick(42)),
                PBMeta.newBuilder()
                        .setType(Type.FILE)
                        .setName("foo")
                        .setParentObjectId(p.oid().toPB())
                        .setFlags(0)
                        .build(), o2.soid()))
                .when(gcr).processReply_(eq(o2), anyDM(), anyDC());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                doNothing().when(gcr).processReply_(eq(o2), anyDM(), anyDC());
                throw new ExNoComponentWithSpecifiedVersion();
            }
        }).when(gcr).processReply_(eq(o1), anyDM(), anyDC());

        dl(o2).download_();

        InOrder ordered = inOrder(gcc, dlstate);

        ordered.verify(gcc).remoteRequestComponent_(eq(o2), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(o2), endpoint(did1), eq(true));

        ordered.verify(gcc).remoteRequestComponent_(eq(o1), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(o1), endpoint(did1), eq(true));

        ordered.verify(gcc).remoteRequestComponent_(eq(o2), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(o2), endpoint(did1), eq(false));

        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldRetryImmediatelyWhenHashComputed() throws Exception
    {
        mockReplies(socid, did1, did1);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                doNothing().when(gcr).processReply_(eq(socid), anyDM(), anyDC());
                throw new ExRestartWithHashComputed("bla");
            }
        }).when(gcr).processReply_(eq(socid), anyDM(), anyDC());

        dl(socid).download_();

        InOrder ordered = inOrder(gcc, dlstate);

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(socid), endpoint(did1), eq(true));

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(socid), endpoint(did1), eq(false));

        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldPropagateLocalException() throws Exception
    {
        mockReplies(socid, did1);

        doThrow(new ExAborted())
                .when(gcr).processReply_(eq(socid), anyDM(), anyDC());

        try {
            dl(socid).download_();
            fail();
        } catch (ExAborted e) {}

        InOrder ordered = inOrder(gcc, dlstate);

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(socid), endpoint(did1), eq(true));

        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldPropagateParentDepError() throws Exception
    {
        final SOCID child = new SOCID(sidx, OID.generate(), CID.META);
        final SOCID parent = new SOCID(sidx, OID.generate(), CID.META);

        mockReplies(child, did1);
        mockReplies(parent, did1);

        doThrow(new ExDependsOn(new OCID(parent.oid(), parent.cid()), DependencyType.PARENT))
                .when(gcr).processReply_(eq(child), anyDM(), anyDC());
        doThrow(new ExNoComponentWithSpecifiedVersion())
                .when(gcr).processReply_(eq(parent), anyDM(), anyDC());

        try {
            dl(child).download_();
            fail();
        } catch (ExUnsatisfiedDependency e) {
            assertEquals(did1, e._did);
            assertEquals(parent, e._socid);
            assertTrue(e.recursiveUnwrap() instanceof ExNoComponentWithSpecifiedVersion);
        }

        InOrder ordered = inOrder(gcc, dlstate);

        ordered.verify(gcc).remoteRequestComponent_(eq(child), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(child), endpoint(did1), eq(true));

        ordered.verify(gcc).remoteRequestComponent_(eq(parent), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(parent), endpoint(did1), eq(true));

        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldPropagateNameConflictDepError() throws Exception
    {
        final SOCID o1 = new SOCID(sidx, OID.generate(), CID.META);
        final SOCID o2 = new SOCID(sidx, OID.generate(), CID.META);
        final SOCID p = new SOCID(sidx, OID.generate(), CID.META);

        mockReplies(o1, did1);
        mockReplies(o2, did1, did1);

        doThrow(new ExNameConflictDependsOn(o1.oid(), p.oid(), Version.of(did1, new Tick(42)),
                PBMeta.newBuilder()
                        .setType(Type.FILE)
                        .setName("foo")
                        .setParentObjectId(p.oid().toPB())
                        .setFlags(0)
                        .build(),
                o2.soid()))
                .when(gcr).processReply_(eq(o2), anyDM(), anyDC());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                doNothing().when(gcr).processReply_(eq(o2), anyDM(), anyDC());
                throw new IOException();
            }
        }).when(gcr).processReply_(eq(o1), anyDM(), anyDC());

        try {
            dl(o2).download_();
            fail();
        } catch (ExUnsatisfiedDependency e) {
            assertEquals(o1, e._socid);
            assertEquals(did1, e._did);
            assertTrue(e.recursiveUnwrap() instanceof IOException);
        }

        InOrder ordered = inOrder(gcc, dlstate);

        ordered.verify(gcc).remoteRequestComponent_(eq(o2), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(o2), endpoint(did1), eq(true));

        ordered.verify(gcc).remoteRequestComponent_(eq(o1), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(o1), endpoint(did1), eq(true));

        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldDetectDeadlock() throws Exception
    {
        final SOCID child = new SOCID(sidx, OID.generate(), CID.META);
        final SOCID parent = new SOCID(sidx, OID.generate(), CID.META);

        mockReplies(child, did1);
        mockReplies(parent, did1);

        doThrow(new ExDependsOn(new OCID(parent.oid(), parent.cid()), DependencyType.PARENT))
                .when(gcr).processReply_(eq(child), anyDM(), anyDC());
        doThrow(new ExDependsOn(new OCID(child.oid(), child.cid()), DependencyType.PARENT))
                .when(gcr).processReply_(eq(parent), anyDM(), anyDC());

        when(ddr.resolveDeadlock_(anyListOf(DependencyEdge.class), anyDC()))
                .thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable
            {
                // simulate deadlock resolution...
                doNothing().when(gcr).processReply_(eq(child), anyDM(), anyDC());
                doNothing().when(gcr).processReply_(eq(parent), anyDM(), anyDC());
                return true;
            }
        });

        dl(child).download_();

        InOrder ordered = inOrder(gcc, dlstate, ddr);

        ordered.verify(gcc).remoteRequestComponent_(eq(child), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(child), endpoint(did1), eq(true));

        ordered.verify(gcc).remoteRequestComponent_(eq(parent), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(parent), endpoint(did1), eq(true));

        ordered.verify(ddr).resolveDeadlock_(anyListOf(DependencyEdge.class), anyDC());

        ordered.verify(gcc).remoteRequestComponent_(eq(parent), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(parent), endpoint(did1), eq(false));

        ordered.verify(gcc).remoteRequestComponent_(eq(child), eq(did1), eq(tk));
        ordered.verify(dlstate).ended_(eq(child), endpoint(did1), eq(false));

        verifyZeroInteractions(dls);
    }
}
