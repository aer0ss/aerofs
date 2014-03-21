/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.rocklog.Defect;
import com.aerofs.rocklog.RockLog;
import com.aerofs.testlib.AbstractTest;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.util.concurrent.Future;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestHashQueue extends AbstractTest
{
    static {
        LogUtil.enableConsoleLogging();
        LogUtil.setLevel(Level.DEBUG);
    }
    @Mock CoreScheduler sched;
    @Mock DirectoryService ds;
    @Mock VersionUpdater vu;
    @Mock NativeVersionControl nvc;
    @Mock TransManager tm;
    @Mock RockLog rl;

    @Mock Trans t;
    @Mock Defect d;

    @InjectMocks HashQueue hq;

    final static ContentHash EMPTY_HASH = new ContentHash(SecUtil.hash(new byte [0]));
    final static byte[] EMPTY_CONTENT = new byte[0];

    SID rootSID = SID.rootSID(UserID.fromInternal("foo@bar.baz"));
    SOID soid = new SOID(new SIndex(0), OID.generate());
    SOKID sokid = new SOKID(soid, KIndex.MASTER);

    @Before
    public void setUp() throws Exception
    {
        MockDS mds = new MockDS(rootSID, ds);

        mds.root().file(soid, "foo", 1);

        when(tm.begin_()).thenReturn(t);

        when(rl.newDefect(anyString())).thenReturn(d);
        when(d.addData(anyString(), anyObject())).thenReturn(d);
    }

    private InjectableFile mockContent(final byte[] c, long mtime) throws Exception
    {
        InjectableFile f = mock(InjectableFile.class);

        when(f.lastModified()).thenReturn(mtime);
        when(f.getLength()).thenReturn((long)c.length);
        when(f.getLengthOrZeroIfNotFile()).thenReturn((long)c.length);
        when(f.newInputStream()).thenAnswer(new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                return new ByteArrayInputStream(c);
            }
        });
        return f;
    }

    Future<AbstractEBSelfHandling> whenHashed()
    {
        final SettableFuture<AbstractEBSelfHandling> ev = SettableFuture.create();
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable
            {
                ev.set((AbstractEBSelfHandling)invocation.getArguments()[0]);
                return null;
            }
        }).when(sched).schedule(any(IEvent.class), anyLong());
        return ev;
    }

    @Test
    public void shouldUpdateMtimeOnlyOnHashMatch() throws Exception
    {
        when(ds.getCAHash_(sokid)).thenReturn(EMPTY_HASH);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        Future<AbstractEBSelfHandling> ev = whenHashed();

        assertTrue(hq.requestHash_(soid, f, f.getLength(), f.lastModified(), EMPTY_HASH, t));

        ev.get().handle_();

        verify(d).send();
        verify(ds).setCA_(eq(sokid), eq(0L), eq(42L), eq(EMPTY_HASH), eq(t));
        verifyZeroInteractions(vu);
    }

    @Test
    public void shouldUpdateOnHashMismatch() throws Exception
    {
        ContentHash oldHash = new ContentHash(SecUtil.hash(new byte[] {0}));
        when(ds.getCAHash_(sokid)).thenReturn(oldHash);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        Future<AbstractEBSelfHandling> ev = whenHashed();

        assertTrue(hq.requestHash_(soid, f, f.getLength(), f.lastModified(), oldHash, t));

        ev.get().handle_();

        verify(d).send();
        verify(ds).setCA_(eq(sokid), eq(0L), eq(42L), eq(EMPTY_HASH), eq(t));
        verify(vu).update_(new SOCKID(sokid, CID.CONTENT), t);
    }

    @Test
    public void shouldMergeDuplicateRequests() throws Exception
    {
        ContentHash oldHash = new ContentHash(SecUtil.hash(new byte[] {0}));
        when(ds.getCAHash_(sokid)).thenReturn(oldHash);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        when(f.getAbsolutePath()).thenReturn("");
        Future<AbstractEBSelfHandling> ev = whenHashed();

        assertTrue(hq.requestHash_(soid, f, f.getLength(), f.lastModified(), oldHash, t));
        assertFalse(hq.requestHash_(soid, f, f.getLength(), f.lastModified(), oldHash, t));
        assertFalse(hq.requestHash_(soid, f, f.getLength(), f.lastModified(), oldHash, t));

        ev.get().handle_();

        verify(d).send();
        verify(ds).setCA_(eq(sokid), eq(0L), eq(42L), eq(EMPTY_HASH), eq(t));
        verify(vu).update_(new SOCKID(sokid, CID.CONTENT), t);
    }

    @Test
    public void shouldAbortAndRestartOnConflictingPathRequest() throws Exception
    {
        ContentHash oldHash = new ContentHash(SecUtil.hash(new byte[] {0}));
        when(ds.getCAHash_(sokid)).thenReturn(oldHash);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        when(f.getAbsolutePath()).thenReturn("foo");
        Future<AbstractEBSelfHandling> evAborted = whenHashed();

        assertTrue(hq.requestHash_(soid, f, f.getLength(), f.lastModified(), oldHash, t));
        evAborted.get();

        Future<AbstractEBSelfHandling> ev = whenHashed();
        f = mockContent(EMPTY_CONTENT, 42L);
        when(f.getAbsolutePath()).thenReturn("bar");
        assertTrue(hq.requestHash_(soid, f, f.getLength(), f.lastModified(), oldHash, t));

        evAborted.get().handle_();

        verifyZeroInteractions(d);
        verify(ds, never()).setCA_(eq(sokid), anyLong(), anyLong(), any(ContentHash.class), eq(t));
        verifyZeroInteractions(vu);

        ev.get().handle_();

        verify(d).send();
        verify(ds).setCA_(eq(sokid), eq(0L), eq(42L), eq(EMPTY_HASH), eq(t));
        verify(vu).update_(new SOCKID(sokid, CID.CONTENT), t);
    }

    @Test
    public void shouldAbortAndRestartOnConflictingMtimeRequest() throws Exception
    {
        ContentHash oldHash = new ContentHash(SecUtil.hash(new byte[] {0}));
        when(ds.getCAHash_(sokid)).thenReturn(oldHash);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        when(f.getAbsolutePath()).thenReturn("");
        Future<AbstractEBSelfHandling> evAborted = whenHashed();

        assertTrue(hq.requestHash_(soid, f, f.getLength(), f.lastModified(), oldHash, t));
        evAborted.get();

        Future<AbstractEBSelfHandling> ev = whenHashed();
        f = mockContent(EMPTY_CONTENT, 43L);
        assertTrue(hq.requestHash_(soid, f, f.getLength(), f.lastModified(), oldHash, t));

        evAborted.get().handle_();

        verifyZeroInteractions(d);
        verify(ds, never()).setCA_(eq(sokid), anyLong(), anyLong(), any(ContentHash.class), eq(t));
        verifyZeroInteractions(vu);

        ev.get().handle_();

        verify(d).send();
        verify(ds).setCA_(eq(sokid), eq(0L), eq(43L), eq(EMPTY_HASH), eq(t));
        verify(vu).update_(new SOCKID(sokid, CID.CONTENT), t);
    }

    @Test
    public void shouldAbortOnLengthMismatch() throws Exception
    {
        when(ds.getCAHash_(sokid)).thenReturn(EMPTY_HASH);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        Future<AbstractEBSelfHandling> ev = whenHashed();

        assertTrue(hq.requestHash_(soid, f, f.getLength(), f.lastModified(), EMPTY_HASH, t));
        ev.get();
        when(f.getLength()).thenReturn(0xdeadL);
        ev.get().handle_();

        verifyZeroInteractions(d);
        verify(ds, never()).setCA_(eq(sokid), anyLong(), anyLong(), any(ContentHash.class), eq(t));
        verifyZeroInteractions(vu);
    }

    @Test
    public void shouldAbortOnTimestampMismatch() throws Exception
    {
        when(ds.getCAHash_(sokid)).thenReturn(EMPTY_HASH);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        Future<AbstractEBSelfHandling> ev = whenHashed();

        assertTrue(hq.requestHash_(soid, f, f.getLength(), f.lastModified(), EMPTY_HASH, t));
        ev.get();
        when(f.lastModified()).thenReturn(43L);
        ev.get().handle_();

        verifyZeroInteractions(d);
        verify(ds, never()).setCA_(eq(sokid), anyLong(), anyLong(), any(ContentHash.class), eq(t));
        verifyZeroInteractions(vu);
    }

    @Test
    public void shouldAbortOnContentVersionUpdate() throws Exception
    {
        when(ds.getCAHash_(sokid)).thenReturn(EMPTY_HASH);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        Future<AbstractEBSelfHandling> ev = whenHashed();

        assertTrue(hq.requestHash_(soid, f, f.getLength(), f.lastModified(), EMPTY_HASH, t));
        ev.get();
        hq.localVersionAdded_(new SOCKID(sokid, CID.CONTENT),
                Version.of(DID.generate(), new Tick(1L)), t);
        ev.get().handle_();

        verifyZeroInteractions(d);
        verify(ds, never()).setCA_(eq(sokid), anyLong(), anyLong(), any(ContentHash.class), eq(t));
        verifyZeroInteractions(vu);
    }
}
