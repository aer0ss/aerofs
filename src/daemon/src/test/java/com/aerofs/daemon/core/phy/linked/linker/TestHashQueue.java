/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.*;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.testlib.AbstractTest;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.util.concurrent.Future;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestHashQueue extends AbstractTest
{
    @Mock CoreScheduler sched;
    @Mock DirectoryService ds;
    @Mock VersionUpdater vu;
    @Mock TransManager tm;
    @InjectMocks HashQueue hq;

    class TestableTrans extends Trans
    {
        private TestableTrans(TransManager tm)
        {
            super(new Trans.Factory(mock(IDBCW.class)), tm);
        }
    }

    final static ContentHash EMPTY_HASH = new ContentHash(BaseSecUtil.hash(new byte [0]));
    final static byte[] EMPTY_CONTENT = new byte[0];

    SID rootSID = SID.rootSID(UserID.fromInternal("foo@bar.baz"));
    SOID soid = new SOID(new SIndex(0), OID.generate());
    SOKID sokid = new SOKID(soid, KIndex.MASTER);

    @Before
    public void setUp() throws Exception
    {
        MockDS mds = new MockDS(rootSID, ds);

        mds.root().file(soid, "foo", 1);

        when(tm.begin_()).thenAnswer(invocation -> new TestableTrans(tm));
    }

    private InjectableFile mockContent(final byte[] c, long mtime) throws Exception {
        return mockFutureContent(c, mtime, null);
    }

    private InjectableFile mockFutureContent(byte[] c, long mtime, Future<Void> w) throws Exception {
        InjectableFile f = mock(InjectableFile.class);

        when(f.lastModified()).thenReturn(mtime);
        when(f.length()).thenReturn((long)c.length);
        when(f.lengthOrZeroIfNotFile()).thenReturn((long)c.length);
        when(f.newInputStream()).thenAnswer(invocation -> {
            if (w != null) w.get();
            return new ByteArrayInputStream(c);
        });
        return f;
    }

    Future<AbstractEBSelfHandling> whenHashed()
    {
        final SettableFuture<AbstractEBSelfHandling> ev = SettableFuture.create();
        doAnswer(invocation -> {
            ev.set((AbstractEBSelfHandling)invocation.getArguments()[0]);
            return null;
        }).when(sched).schedule(any(IEvent.class), anyLong());
        return ev;
    }

    @FunctionalInterface
    private static interface Op
    {
        void exec(Trans t) throws Exception;
    }

    void trans(Op r) throws Exception
    {
        Trans t = tm.begin_();
        try {
            r.exec(t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    @Test
    public void shouldNotHashWhenTransactionAborted() throws Exception
    {
        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        try {
            trans(t -> {
                hq.requestHash_(soid, f, f.length(), f.lastModified(), t);
                throw new Exception();
            });
            fail();
        } catch (Exception e) {
            verifyZeroInteractions(ds, vu, sched);
        }
    }

    @Test
    public void shouldUpdateMtimeOnlyOnHashMatch() throws Exception
    {
        when(ds.getCAHash_(sokid)).thenReturn(EMPTY_HASH);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        Future<AbstractEBSelfHandling> ev = whenHashed();

        trans(t -> assertTrue(hq.requestHash_(soid, f, f.length(), f.lastModified(), t)));

        ev.get().handle_();

        verify(ds).setCA_(eq(sokid), eq(0L), eq(42L), eq(EMPTY_HASH), any(Trans.class));
        verifyZeroInteractions(vu);
    }

    @Test
    public void shouldUpdateOnHashMismatch() throws Exception
    {
        ContentHash oldHash = new ContentHash(BaseSecUtil.hash(new byte[] {0}));
        when(ds.getCAHash_(sokid)).thenReturn(oldHash);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        Future<AbstractEBSelfHandling> ev = whenHashed();

        trans(t -> assertTrue(hq.requestHash_(soid, f, f.length(), f.lastModified(), t)));

        ev.get().handle_();

        verify(ds).setCA_(eq(sokid), eq(0L), eq(42L), eq(EMPTY_HASH), any(Trans.class));
        verify(vu).update_(eq(new SOCID(soid, CID.CONTENT)), any(Trans.class));
    }

    @Test
    public void shouldMergeDuplicateRequests() throws Exception
    {
        ContentHash oldHash = new ContentHash(BaseSecUtil.hash(new byte[] {0}));
        when(ds.getCAHash_(sokid)).thenReturn(oldHash);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        when(f.getAbsolutePath()).thenReturn("");
        Future<AbstractEBSelfHandling> ev = whenHashed();

        trans(t -> assertTrue(hq.requestHash_(soid, f, f.length(), f.lastModified(), t)));
        trans(t -> assertFalse(hq.requestHash_(soid, f, f.length(), f.lastModified(), t)));
        trans(t -> assertFalse(hq.requestHash_(soid, f, f.length(), f.lastModified(), t)));

        ev.get().handle_();

        verify(ds).setCA_(eq(sokid), eq(0L), eq(42L), eq(EMPTY_HASH), any(Trans.class));
        verify(vu).update_(eq(new SOCID(soid, CID.CONTENT)), any(Trans.class));
    }

    @Test
    public void shouldAbortAndRestartOnConflictingPathRequest() throws Exception
    {
        ContentHash oldHash = new ContentHash(BaseSecUtil.hash(new byte[] {0}));
        when(ds.getCAHash_(sokid)).thenReturn(oldHash);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        when(f.getAbsolutePath()).thenReturn("foo");
        Future<AbstractEBSelfHandling> evAborted = whenHashed();

        trans(t -> assertTrue(hq.requestHash_(soid, f, f.length(), f.lastModified(), t)));
        evAborted.get();

        Future<AbstractEBSelfHandling> ev = whenHashed();
        SettableFuture<Void> w = SettableFuture.create();
        InjectableFile g = mockFutureContent(EMPTY_CONTENT, 42L, w);
        when(g.getAbsolutePath()).thenReturn("bar");
        trans(t -> assertTrue(hq.requestHash_(soid, g, g.length(), g.lastModified(), t)));

        evAborted.get().handle_();
        w.set(null); // for proper abort testing, make sure the second request doesn't complete too fast

        verify(ds, never()).setCA_(eq(sokid), anyLong(), anyLong(), any(ContentHash.class), any(Trans.class));
        verifyZeroInteractions(vu);

        ev.get().handle_();

        verify(ds).setCA_(eq(sokid), eq(0L), eq(42L), eq(EMPTY_HASH), any(Trans.class));
        verify(vu).update_(eq(new SOCID(soid, CID.CONTENT)), any(Trans.class));
    }

    @Test
    public void shouldAbortAndRestartOnConflictingMtimeRequest() throws Exception
    {
        ContentHash oldHash = new ContentHash(BaseSecUtil.hash(new byte[] {0}));
        when(ds.getCAHash_(sokid)).thenReturn(oldHash);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        when(f.getAbsolutePath()).thenReturn("");
        Future<AbstractEBSelfHandling> evAborted = whenHashed();

        trans(t -> assertTrue(hq.requestHash_(soid, f, f.length(), f.lastModified(), t)));
        evAborted.get();

        Future<AbstractEBSelfHandling> ev = whenHashed();
        SettableFuture<Void> w = SettableFuture.create();
        InjectableFile g = mockFutureContent(EMPTY_CONTENT, 43L, w);
        trans(t -> assertTrue(hq.requestHash_(soid, g, g.length(), g.lastModified(), t)));

        evAborted.get().handle_();
        w.set(null); // for proper abort testing, make sure the second request doesn't complete too fast

        verify(ds, never()).setCA_(eq(sokid), anyLong(), anyLong(), any(ContentHash.class), any(Trans.class));
        verifyZeroInteractions(vu);

        ev.get().handle_();

        verify(ds).setCA_(eq(sokid), eq(0L), eq(43L), eq(EMPTY_HASH), any(Trans.class));
        verify(vu).update_(eq(new SOCID(soid, CID.CONTENT)), any(Trans.class));
    }

    @Test
    public void shouldAbortOnPhysicalChange() throws Exception
    {
        when(ds.getCAHash_(sokid)).thenReturn(EMPTY_HASH);

        InjectableFile f = mockContent(EMPTY_CONTENT, 42L);
        Future<AbstractEBSelfHandling> ev = whenHashed();

        trans(t -> assertTrue(hq.requestHash_(soid, f, f.length(), f.lastModified(), t)));
        ev.get();
        when(f.wasModifiedSince(42L, 0L)).thenReturn(true);
        ev.get().handle_();

        verify(ds, never()).setCA_(eq(sokid), anyLong(), anyLong(), any(ContentHash.class), any(Trans.class));
        verifyZeroInteractions(vu);
    }
}
