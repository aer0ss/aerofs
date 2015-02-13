/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.polaris.PolarisClient;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.MetaBufferDatabase;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase.MetaChange;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.testlib.AbstractBaseTest;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;

import static com.aerofs.daemon.core.polaris.api.RemoteChange.insert;
import static com.aerofs.daemon.core.polaris.api.RemoteChange.remove;
import static com.aerofs.daemon.core.polaris.api.RemoteChange.rename;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestMetaChangeSubmitter extends AbstractBaseTest
{
    private final PolarisClient client = mock(PolarisClient.class);
    private final MetaChangesDatabase mcdb = mock(MetaChangesDatabase.class);
    private final MetaBufferDatabase mbdb = mock(MetaBufferDatabase.class);
    private final RemoteLinkDatabase rldb = mock(RemoteLinkDatabase.class);
    private final IMapSIndex2SID sidx2sid = mock(IMapSIndex2SID.class);
    private final CentralVersionDatabase cvdb = mock(CentralVersionDatabase.class);
    private final DirectoryService ds = mock(DirectoryService.class);
    private final MapAlias2Target a2t = mock(MapAlias2Target.class);
    private final TransManager tm = mock(TransManager.class);
    private final PauseSync pause = mock(PauseSync.class);

    private final Trans t = mock(Trans.class);

    private MetaChangeSubmitter mcs;

    private SIndex sidx = new SIndex(1);
    private SID sid = SID.generate();

    @Before
    public void setUp() throws Exception
    {
        when(tm.begin_()).thenReturn(t);
        when(sidx2sid.get_(sidx)).thenReturn(sid);
        when(mcdb.deleteChange_(any(SIndex.class), anyLong(), eq(t))).thenReturn(true);
        when(a2t.dereferenceAliasedOID_(any(SOID.class)))
                .thenAnswer(invocation -> invocation.getArguments()[0]);

        mcs = new MetaChangeSubmitter(client, mcdb, mbdb, rldb, cvdb, sidx2sid, a2t, pause, ds, tm);
    }

    void givenLocalChanges(MetaChange... c) throws SQLException
    {
        when(mcdb.getChangesSince_(sidx, 0)).thenReturn(new IDBIterator<MetaChange>() {
            int idx = -1;
            @Override
            public MetaChange get_() throws SQLException { return c[idx]; }

            @Override
            public boolean next_() throws SQLException { return ++idx < c.length; }

            @Override
            public void close_() throws SQLException{}

            @Override
            public boolean closed_() { return false; }
        });
    }

    private MetaChange move(OID oid, OID newParent, String newName)
    {
        return new MetaChange(sidx, 0, oid, newParent.getBytes(), newName);
    }

    private MetaChange delete(OID oid)
    {
        return new MetaChange(sidx, 0, oid, OID.TRASH.getBytes(), oid.toStringFormal());
    }

    private boolean ackRemoteChange(RemoteChange rc, RemoteLink lnk) throws Exception
    {
        return mcs.ackMatchingSubmittedMetaChange_(sidx, rc, lnk, t);
    }

    @Test
    public void shouldNotAckWhenNoLocalChanges() throws Exception
    {
        givenLocalChanges();

        OID oid = OID.generate();

        assertFalse(ackRemoteChange(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER),
                null));

        verifyZeroInteractions(rldb, cvdb);
    }

    @Test
    public void shouldNotAckOnMismatch() throws Exception
    {
        OID oid = OID.generate();

        givenLocalChanges(delete(oid));

        assertFalse(ackRemoteChange(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER),
                new RemoteLink(OID.ROOT, "foo", 42)));

        verifyZeroInteractions(rldb, cvdb);
    }

    @Test
    public void shouldAckOnMatch_create() throws Exception
    {
        OID oid = OID.generate();

        givenLocalChanges(move(oid, OID.ROOT, "foo"));

        OA oa = mock(OA.class);
        when(oa.type()).thenReturn(Type.DIR);
        when(ds.getOA_(new SOID(sidx, oid)))
                .thenReturn(oa);

        assertTrue(ackRemoteChange(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER),
                null));

        verify(rldb).insertParent_(sidx, oid, OID.ROOT, "foo", 0, t);
    }

    @Test
    public void shouldAckOnMatch_move() throws Exception
    {
        OID oid = OID.generate();

        givenLocalChanges(move(oid, OID.ROOT, "foo"));

        assertTrue(ackRemoteChange(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER),
                new RemoteLink(OID.generate(), "bar", 42)));

        verify(rldb).updateParent_(sidx, oid, OID.ROOT, "foo", 0, t);
    }

    @Test
    public void shouldAckOnMatch_rename() throws Exception
    {
        OID oid = OID.generate();

        givenLocalChanges(move(oid, OID.ROOT, "foo"));

        assertTrue(ackRemoteChange(
                rename(OID.ROOT, "foo", oid),
                new RemoteLink(OID.ROOT, "bar", 42)));

        verify(rldb).updateParent_(sidx, oid, OID.ROOT, "foo", 0, t);
    }

    @Test
    public void shouldAckOnMatch_remove() throws Exception
    {
        OID oid = OID.generate();

        givenLocalChanges(delete(oid));

        assertTrue(ackRemoteChange(
                remove(OID.ROOT, oid),
                new RemoteLink(OID.ROOT, "foo", 42)));

        verify(mcdb).deleteChange_(eq(sidx), anyLong(), eq(t));
        verify(rldb).removeParent_(sidx, oid, t);
    }
}
