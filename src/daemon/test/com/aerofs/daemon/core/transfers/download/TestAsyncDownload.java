/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExOutOfSpace;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Map;

import static com.aerofs.daemon.core.lib.AsyncDownloadTestHelper.anyDM;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * AsyncDownload contract:
 *   - tries to get rid of all KMLs about an object by asking a given set of device
 *   - call appropriate listeners on success/failure
 *   N.B. Unlike some other classes that share code with corresponding storage agent tests,
 *   this one doesn't because while the storage agent tests for things similar to this class,
 *   the internals of the tests are different enough to offset value of code sharing.
 */
public class TestAsyncDownload extends AbstractDownloadTest
{
    final SOID soid = new SOID(sidx, OID.generate());
    final SOCID socid = new SOCID(soid, CID.CONTENT);

    @Before
    public void setUp() throws Exception
    {
        OA oa = mock(OA.class);
        when(oa.soid()).thenReturn(soid);
        when(oa.parent()).thenReturn(OID.ROOT);
        when(ds.getOA_(soid)).thenReturn(oa);
        when(ds.getOANullable_(soid)).thenReturn(oa);
        when(ds.getAliasedOANullable_(soid)).thenReturn(oa);
    }

    @Test
    public void shouldDownload() throws Exception
    {
        when(changes.hasRemoteChanges_(socid)).thenReturn(false);

        mockReplies(socid, did1);

        asyncdl(socid)
                .do_();

        InOrder ordered = inOrder(gcc, dcl);

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did1), eq(tk));
        ordered.verify(dcl).onPartialDownloadSuccess_(socid, did1);
        ordered.verify(dcl).onDownloadSuccess_(socid, did1);
        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldTrySecondDeviceWhenKMLsLeft() throws Exception
    {
        mockReplies(socid, did1, did2);
        when(changes.hasRemoteChanges_(socid))
                .thenReturn(true)
                .thenReturn(false);

        asyncdl(socid)
                .do_();

        InOrder ordered = inOrder(gcc, dcl);

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did1), eq(tk));
        ordered.verify(dcl).onPartialDownloadSuccess_(socid, did1);

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did2), eq(tk));
        ordered.verify(dcl).onPartialDownloadSuccess_(socid, did2);

        ordered.verify(dcl).onDownloadSuccess_(socid, did2);
        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldTrySecondDeviceWhenFirstFails() throws Exception
    {
        when(changes.hasRemoteChanges_(socid)).thenReturn(false);

        mockReplies(socid, did1, did2);
        doAnswer(invocation -> {
            doNothing().when(gcr)
                    .processResponse_(eq(socid), anyDM(), anyDC());
            throw new Exception();
        }).when(gcr).processResponse_(eq(socid), anyDM(), anyDC());

        asyncdl(socid)
                .do_();

        InOrder ordered = inOrder(gcc, dcl);

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did1), eq(tk));
        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did2), eq(tk));
        ordered.verify(dcl).onPartialDownloadSuccess_(socid, did2);
        ordered.verify(dcl).onDownloadSuccess_(socid, did2);
        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldTryAllDevicesWhenKMLsLeft() throws Exception
    {
        mockReplies(socid, did1, did2, did3);

        when(changes.hasRemoteChanges_(socid)).thenReturn(true);

        asyncdl(socid)
                .do_();

        InOrder ordered = inOrder(gcc, dcl);

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did1), eq(tk));
        ordered.verify(dcl).onPartialDownloadSuccess_(socid, did1);

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did2), eq(tk));
        ordered.verify(dcl).onPartialDownloadSuccess_(socid, did2);

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did3), eq(tk));
        ordered.verify(dcl).onPartialDownloadSuccess_(socid, did3);

        ordered.verify(dcl).onPerDeviceErrors_(socid, ImmutableMap.<DID, Exception>of());
        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldTryAllDevicesOnFailure() throws Exception
    {
        final DID[] dids = {did1, did2, did3};
        final Map<DID, Exception> did2e = ImmutableMap.of(
                did1, new Exception("foo"),
                did2, new Exception("bar"),
                did3, new Exception("baz"));

        mockReplies(socid, did1, did2, did3);
        doAnswer(new Answer<Void>() {
            int idx = 0;
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                throw did2e.get(dids[idx++]);
            }
        }).when(gcr).processResponse_(eq(socid), anyDM(), anyDC());

        asyncdl(socid)
                .do_();

        InOrder ordered = inOrder(gcc, dcl);

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did1), eq(tk));

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did2), eq(tk));

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did3), eq(tk));

        ordered.verify(dcl).onPerDeviceErrors_(socid, did2e);
        verifyZeroInteractions(dls);
    }

    @Test
    public void shouldNotTryAllDevicesOnLocalFailure() throws Exception
    {
        mockReplies(socid, did1);

        ExOutOfSpace ex = new ExOutOfSpace();
        doThrow(ex).when(gcr).processResponse_(eq(socid), anyDM(), anyDC());

        asyncdl(socid)
                .do_();

        InOrder ordered = inOrder(gcc, dcl);

        ordered.verify(gcc).remoteRequestComponent_(eq(socid), eq(did1), eq(tk));
        ordered.verify(dcl).onGeneralError_(socid, ex);
        verifyZeroInteractions(dls);
    }
}
