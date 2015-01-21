/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryServiceImpl;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.polaris.InMemoryDS;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.MetaBufferDatabase;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.core.polaris.submit.MetaChangeSubmitter;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.AliasDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.testlib.AbstractBaseTest;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static com.aerofs.daemon.core.polaris.InMemoryDS.file;
import static com.aerofs.daemon.core.polaris.InMemoryDS.folder;
import static com.aerofs.daemon.core.polaris.api.RemoteChange.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class TestApplyChange extends AbstractBaseTest
{
    static {
        // Change to DEBUG if you're writing a test, but keep at NONE otherwise.
        LogUtil.setLevel(Level.DEBUG);
        LogUtil.enableConsoleLogging();
    }

    final CfgUsePolaris usePolaris = new CfgUsePolaris() {
        @Override public boolean get() { return true; }
    };
    final InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW(mock(InjectableDriver.class), usePolaris);
    final CoreDBCW cdbcw = dbcw.getCoreDBCW();

    final UserID user = UserID.fromInternal("foo@bar.baz");
    final SID rootSID = SID.rootSID(user);

    final IPhysicalStorage ps = mock(IPhysicalStorage.class);

    final InMemoryDS mds = new InMemoryDS(dbcw.getCoreDBCW(), usePolaris, ps, user);

    final RemoteLinkDatabase rldb = new RemoteLinkDatabase(cdbcw, mds.sdo);
    final MetaBufferDatabase mbdb = new MetaBufferDatabase(cdbcw, mds.sdo);
    final CentralVersionDatabase cvdb = new CentralVersionDatabase(cdbcw, mds.sdo);
    final AliasDatabase adb = new AliasDatabase(cdbcw);
    final ContentChangesDatabase ccdb = new ContentChangesDatabase(cdbcw, mds.sco, mds.sdo);
    final RemoteContentDatabase rcdb = new RemoteContentDatabase(cdbcw, mds.sco, mds.sdo);

    final MapAlias2Target a2t = new MapAlias2Target(adb);
    final MetaChangesDatabase mcdb = mock(MetaChangesDatabase.class);
    final MetaChangeSubmitter submitter = mock(MetaChangeSubmitter.class);

    final Expulsion expulsion = mock(Expulsion.class);
    final Trans t = mock(Trans.class);

    final DirectoryServiceImpl ds = spy(mds.ds);
    IPhysicalFolder pf = mock(IPhysicalFolder.class);

    ApplyChange ac;

    SIndex sidx;

    @Before
    public void setUp() throws Exception
    {
        when(ps.newFolder_(any(ResolvedPath.class))).thenReturn(pf);

        dbcw.init_();

        mds.stores.init_();
        try {
            mds.sc.createRootStore_(rootSID, "", mock(Trans.class));
        } catch (Exception e) { throw new AssertionError(e); }
        sidx = mds.sm.get_(rootSID);

        ac = new ApplyChange(ds, ps, expulsion, cvdb, rldb, a2t, ds, mbdb, mcdb, submitter, ccdb,
                rcdb, mock(MapSIndex2Store.class));
    }

    @After
    public void tearDown() throws Exception
    {
        dbcw.fini_();
    }

    static class PolarisState
    {
        Map<UniqueID, Long> versions = Maps.newHashMap();
        List<RemoteChange> changes = Lists.newArrayList();

        void add(RemoteChange rc)
        {
            long v = versions.getOrDefault(rc.oid, 0L) + 1;
            versions.put(rc.oid, v);
            rc.newVersion = v;
            rc.logicalTimestamp = changes.size() + 1;
            changes.add(rc);
        }

        void add(RemoteChange... rcl)
        {
            for (RemoteChange rc : rcl) add(rc);
        }
    }

    private final PolarisState state = new PolarisState();

    private void apply(int from, int to) throws Exception
    {
        long maxLTS = state.changes.size();
        for (RemoteChange rc : state.changes.subList(from, to)) {
            ac.apply_(sidx, rc, maxLTS, t);
        }
    }

    private void apply(RemoteChange... changes) throws Exception
    {
        int min = state.changes.size();
        state.add(changes);
        apply(min, state.changes.size());
    }

    static Matcher<OA> isAt(OID parent, String name, OA.Type type)
    {
        return new BaseMatcher<OA>()
        {
            @Override
            public boolean matches(Object o)
            {
                return o != null && o instanceof OA
                        && parent.equals(((OA)o).parent())
                        && name.equals(((OA)o).name())
                        && type == ((OA)o).type();
            }

            @Override
            public void describeTo(Description description)
            {
                description.appendText("at(").appendValue(parent).appendValue(name).appendText(")");
            }
        };
    }

    private void assertOAEquals(OID oid, OID parent, String name, OA.Type type) throws SQLException
    {
        assertThat(ds.getOANullable_(new SOID(sidx, oid)), isAt(parent, name, type));
    }

    private void assertIsBuffered(boolean yes, OID... oids) throws SQLException
    {
        for (OID o : oids) assertEquals(o.toString(), yes, mbdb.isBuffered_(new SOID(sidx, o)));
    }

    private void assertNotPresent(OID... oids) throws SQLException
    {
        for (OID o : oids) assertNull(ds.getOANullable_(new SOID(sidx, o)));
    }
    
    private void assertHasRemoteLink(OID oid, OID parent, String name, long logicalTimestamp)
            throws SQLException
    {
        assertEquals(new RemoteLink(parent,name, logicalTimestamp), rldb.getParent_(sidx, oid));
    }

    @Test
    public void shouldInsertImmediately() throws Exception
    {
        OID oid = OID.generate();
        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER)
        );

        // verify
        mds.expect(rootSID,
                folder("foo", oid));
        assertHasRemoteLink(oid, OID.ROOT, "foo", 1);
        assertIsBuffered(false, oid);
    }

    @Test
    public void shouldBufferInsert() throws Exception
    {
        when(mcdb.hasChanges_(sidx)).thenReturn(true);

        OID oid = OID.generate();
        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER)
        );

        // verify
        assertNotPresent(oid);
        assertHasRemoteLink(oid, OID.ROOT, "foo", 1);
        assertIsBuffered(true, oid);
    }

    @Test
    public void shouldCreateHierarchyImmediately() throws Exception
    {
        OID gp = OID.generate(), p = OID.generate(), a = OID.generate(), b = OID.generate();

        apply(
                insert(OID.ROOT, "foo", gp, ObjectType.FOLDER),
                insert(gp, "bar", p, ObjectType.FOLDER),
                insert(p, "baz", a, ObjectType.FILE),
                insert(p, "qux", b, ObjectType.FILE)
        );

        // verify
        mds.expect(rootSID,
                folder("foo", gp,
                        folder("bar",  p,
                                file("baz", a),
                                file("qux", b))));
        assertHasRemoteLink(gp, OID.ROOT, "foo", 1);
        assertHasRemoteLink(p, gp, "bar", 2);
        assertHasRemoteLink(a, p, "baz", 3);
        assertHasRemoteLink(b, p, "qux", 4);
        assertIsBuffered(false, gp, p, a, b);
    }

    @Test
    public void shouldBufferHierarchy() throws Exception
    {
        when(mcdb.hasChanges_(sidx)).thenReturn(true);

        OID gp = OID.generate(), p = OID.generate(), a = OID.generate(), b = OID.generate();

        apply(
                insert(OID.ROOT, "foo", gp, ObjectType.FOLDER),
                insert(gp, "bar", p, ObjectType.FOLDER),
                insert(p, "baz", a, ObjectType.FILE),
                insert(p, "qux", b, ObjectType.FILE)
        );

        // verify
        assertNotPresent(gp, p, a, b);

        assertHasRemoteLink(gp, OID.ROOT, "foo", 1);
        assertHasRemoteLink(p, gp, "bar", 2);
        assertHasRemoteLink(a, p, "baz", 3);
        assertHasRemoteLink(b, p, "qux", 4);
        assertIsBuffered(true, gp, p, a, b);
    }

    @Test
    public void shouldCreateBufferedHierarchy() throws Exception
    {
        when(mcdb.hasChanges_(sidx)).thenReturn(true);

        OID gp = OID.generate(), p = OID.generate(), a = OID.generate(), b = OID.generate();

        apply(
                insert(OID.ROOT, "foo", gp, ObjectType.FOLDER),
                insert(gp, "bar", p, ObjectType.FOLDER),
                insert(p, "baz", a, ObjectType.FILE),
                insert(p, "qux", b, ObjectType.FILE)
        );
        ac.applyBufferedChanges_(sidx, 42, t);

        // verify
        mds.expect(rootSID,
                folder("foo", gp,
                        folder("bar", p,
                                file("baz", a),
                                file("qux", b))));
        assertHasRemoteLink(gp, OID.ROOT, "foo", 1);
        assertHasRemoteLink(p, gp, "bar", 2);
        assertHasRemoteLink(a, p, "baz", 3);
        assertHasRemoteLink(b, p, "qux", 4);
        assertIsBuffered(false, gp, p, a, b);
    }

    @Test
    public void shouldHandleMoveeOfBufferedObject() throws Exception
    {
        when(mcdb.hasChanges_(sidx)).thenReturn(true);

        OID p = OID.generate(), c = OID.generate();

        apply(
                insert(OID.ROOT, "foo", p, ObjectType.FOLDER),
                insert(p, "bar", c, ObjectType.FOLDER),
                insert(OID.ROOT, "baz", c, ObjectType.FOLDER),
                remove(p, c)
        );
        ac.applyBufferedChanges_(sidx, 42, t);

        // verify
        mds.expect(rootSID, folder("foo", p), folder("baz", c));
        assertHasRemoteLink(p, OID.ROOT, "foo", 1);
        assertHasRemoteLink(c, OID.ROOT, "baz", 3);
        assertIsBuffered(false, p, c);
    }

    @Test
    public void shouldHandleRenameOfBufferedObject() throws Exception
    {
        when(mcdb.hasChanges_(sidx)).thenReturn(true);

        OID p = OID.generate(), c = OID.generate();

        apply(
                insert(OID.ROOT, "foo", p, ObjectType.FOLDER),
                insert(p, "bar", c, ObjectType.FOLDER),
                rename(p, "baz", c)
        );
        ac.applyBufferedChanges_(sidx, 42, t);

        // verify
        mds.expect(rootSID,
                folder("foo", p,
                        folder("baz", c)));
        assertHasRemoteLink(p, OID.ROOT, "foo", 1);
        assertHasRemoteLink(c, p, "baz", 3);
        assertIsBuffered(false, p, c);
    }

    @Test
    public void shouldHandleDeleteOfBufferedObject() throws Exception
    {
        when(mcdb.hasChanges_(sidx)).thenReturn(true);

        OID p = OID.generate(), c = OID.generate();

        apply(
                insert(OID.ROOT, "foo", p, ObjectType.FOLDER),
                insert(p, "bar", c, ObjectType.FOLDER),
                remove(p, c)
        );
        ac.applyBufferedChanges_(sidx, 42, t);

        // verify
        mds.expect(rootSID,
                folder("foo", p));
        assertHasRemoteLink(p, OID.ROOT, "foo", 1);
        assertIsBuffered(false, p, c);
    }

    @Test
    public void shouldRenameImmediately() throws Exception
    {
        OID oid = OID.generate();
        ds.createOA_(OA.Type.DIR, sidx, oid, OID.ROOT, "foo", t);
        rldb.insertParent_(sidx, oid, OID.ROOT, "foo", state.changes.size(), t);
        state.versions.put(OID.ROOT, 1L);

        apply(
                rename(OID.ROOT, "bar", oid)
        );

        // verify
        mds.expect(rootSID,
                folder("bar", oid));
        assertHasRemoteLink(oid, OID.ROOT, "bar", 1);
        assertIsBuffered(false, oid);
    }

    @Test
    public void shouldCompactRenameWhenBuffering() throws Exception
    {
        OID oid = OID.generate();
        rldb.insertParent_(sidx, oid, OID.ROOT, "foo", state.changes.size(), t);
        mbdb.insert_(sidx, oid, OA.Type.DIR, 42, t);
        state.versions.put(OID.ROOT, 1L);

        apply(
                rename(OID.ROOT, "bar", oid)
        );

        // verify
        assertNotPresent(oid);
        assertNull(ds.getChild_(sidx, OID.ROOT, "foo"));

        assertHasRemoteLink(oid, OID.ROOT, "bar", 1);
        assertIsBuffered(true, oid);
    }

    @Test
    public void shouldMoveImmediately() throws Exception
    {
        OID p = OID.generate(), oid = OID.generate();

        apply(
                insert(OID.ROOT, "foo", p, ObjectType.FOLDER),
                insert(p, "bar", oid, ObjectType.FOLDER)
        );

        mds.expect(rootSID,
                folder("foo",
                        folder("bar")));

        apply(
                insert(OID.ROOT, "baz", oid, ObjectType.FOLDER),
                remove(p, oid)
        );

        // verify
        mds.expect(rootSID,
                folder("foo", p),
                folder("baz", oid));
        assertHasRemoteLink(oid, OID.ROOT, "baz", 3);
        assertIsBuffered(false, oid);
    }

    @Test
    public void shouldNotApplyBufferedChange() throws Exception
    {
        when(mcdb.hasChanges_(sidx)).thenReturn(true);

        OID oid = OID.generate();

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER)
        );
        ac.applyBufferedChanges_(sidx, 0, t);

        // verify
        assertNotPresent(oid);
        assertNull(ds.getChild_(sidx, OID.ROOT, "bar"));
        assertHasRemoteLink(oid, OID.ROOT, "foo", 1);
        assertIsBuffered(true, oid);
    }

    @Test
    public void shouldApplyBufferedChange() throws Exception
    {
        when(mcdb.hasChanges_(sidx)).thenReturn(true);

        OID oid = OID.generate();

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER),
                rename(OID.ROOT, "bar", oid)
        );
        ac.applyBufferedChanges_(sidx, 42, t);

        // verify
        mds.expect(rootSID,
                folder("bar", oid));
        assertHasRemoteLink(oid, OID.ROOT, "bar", 2);
        assertIsBuffered(false, oid);
    }

    @Test
    public void shouldDeleteChild() throws Exception
    {
        OID oid = OID.generate();
        rldb.insertParent_(sidx, oid, OID.ROOT, "foo", state.changes.size(), t);
        ds.createOA_(OA.Type.DIR, sidx, oid, OID.ROOT, "foo", t);
        state.versions.put(OID.ROOT, 1L);

        apply(
                remove(OID.ROOT, oid)
        );

        // verify
        OA oa = ds.getOANullable_(new SOID(sidx, oid));
        assertNotNull(oa);
        assertTrue(oa.isExpelled());
        assertEquals(oid.toStringFormal(), oa.name());
        assertEquals(OID.TRASH, oa.parent());
        assertNull(ds.getChild_(sidx, OID.ROOT, "foo"));

        // change to remote logical tree
        assertNull(rldb.getParent_(sidx, oid));
        assertIsBuffered(false, oid);
    }

    @Test
    public void shouldCompactDeleteWhenBuffering() throws Exception
    {
        // force buffering
        when(mcdb.hasChanges_(sidx)).thenReturn(true);

        OID oid = OID.generate();

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER),
                remove(OID.ROOT, oid)
        );

        // verify
        assertNull(ds.getOANullable_(new SOID(sidx, oid)));

        // change to remote logical tree
        assertNull(rldb.getParent_(sidx, oid));
        assertIsBuffered(true, oid);
    }

    @Test
    public void shouldInsertBufferedObjectUnderTrash() throws Exception
    {
        // force buffering
        when(mcdb.hasChanges_(sidx)).thenReturn(true);

        OID oid = OID.generate();

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER),
                remove(OID.ROOT, oid)
        );
        ac.applyBufferedChanges_(sidx, 42, t);

        // verify
        OA oa = ds.getOANullable_(new SOID(sidx, oid));
        assertNotNull(oa);
        assertTrue(oa.isExpelled());
        assertEquals(oid.toStringFormal(), oa.name());
        assertEquals(OID.TRASH, oa.parent());
        assertNull(ds.getChild_(sidx, OID.ROOT, "foo"));

        // change to remote logical tree
        assertNull(rldb.getParent_(sidx, oid));
        assertIsBuffered(false, oid);
    }

    @Test
    public void shouldAlias() throws Exception
    {
        when(mcdb.hasChanges_(sidx)).thenReturn(true);
        OID alias = OID.generate();
        ds.createOA_(OA.Type.DIR, sidx, alias, OID.ROOT, "foo", t);

        OID oid = OID.generate();

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER)
        );
        ac.applyBufferedChanges_(sidx, 42, t);

        assertNotPresent(alias);
        mds.expect(rootSID,
                folder("foo"));
    }

    @Test
    public void shouldAliasHierarchy() throws Exception
    {
        when(mcdb.hasChanges_(sidx)).thenReturn(true);
        mds.create(rootSID,
                folder("foo",
                        folder("bar",
                                file("qux"))));

        SOID lgp = ds.resolveNullable_(Path.fromString(rootSID, "foo"));
        SOID lp = ds.resolveNullable_(Path.fromString(rootSID, "foo/bar"));
        SOID b = ds.resolveNullable_(Path.fromString(rootSID, "foo/bar/qux"));

        OID gp = OID.generate(), p = OID.generate(), a = OID.generate();

        apply(
                insert(OID.ROOT, "foo", gp, ObjectType.FOLDER),
                insert(gp, "bar", p, ObjectType.FOLDER),
                insert(p, "baz", a, ObjectType.FILE)
        );

        // verify
        assertNotPresent(gp, p, a);
        assertHasRemoteLink(gp, OID.ROOT, "foo", 1);
        assertHasRemoteLink(p, gp, "bar", 2);
        assertHasRemoteLink(a, p, "baz", 3);
        assertIsBuffered(true, gp, p, a);

        ac.applyBufferedChanges_(sidx, 42, t);

        mds.expect(rootSID,
                folder("foo", gp,
                        folder("bar", p,
                                file("baz", a),
                                file("qux", b.oid()))));
        assertHasRemoteLink(gp, OID.ROOT, "foo", 1);
        assertHasRemoteLink(p, gp, "bar", 2);
        assertHasRemoteLink(a, p, "baz", 3);
        assertIsBuffered(false, gp, p, a);
        assertEquals(new SOID(sidx, gp), a2t.dereferenceAliasedOID_(lgp));
        assertEquals(new SOID(sidx, p), a2t.dereferenceAliasedOID_(lp));
        assertEquals(new SOID(sidx, a), a2t.dereferenceAliasedOID_(new SOID(sidx, a)));
        assertEquals(b, a2t.dereferenceAliasedOID_(b));
    }

    @Test
    public void shouldRenameLocalObject() throws Exception
    {
        when(mcdb.hasChanges_(sidx)).thenReturn(true);
        OID local = OID.generate();
        ds.createOA_(OA.Type.FILE, sidx, local, OID.ROOT, "foo", t);

        OID remote = OID.generate();

        apply(
                insert(OID.ROOT, "foo", remote, ObjectType.FOLDER)
        );
        ac.applyBufferedChanges_(sidx, 42, t);

        mds.expect(rootSID,
                folder("foo", remote),
                file("foo (2)", local));
    }

    /**
     * Race condition:
     *
     * Polaris:
     *   - insert o1:foo
     *   - rename o1
     *
     * Client:
     *   - fetch changes
     *   - insert o2:foo -> accepted
     *   - receive changes NOT including o1's rename
     *
     * o1 is renamed locally to solve the conflict
     * this rename is guaranteed to be eventually fixed
     */
    @Test
    public void shouldRenameRemoteObject() throws Exception
    {
        OID local = OID.generate();
        ds.createOA_(OA.Type.DIR, sidx, local, OID.ROOT, "foo", t);
        rldb.insertParent_(sidx, local, OID.ROOT, "foo", 2, t);

        OID remote = OID.generate();

        apply(
                insert(OID.ROOT, "foo", remote, ObjectType.FOLDER)
        );
        ac.applyBufferedChanges_(sidx, 1, t);

        mds.expect(rootSID,
                folder("foo (2)", remote),
                folder("foo", local));
    }

    @Test
    public void shouldHandleNameSwap() throws Exception
    {
        OID a = OID.generate(), b = OID.generate();
        apply(
                insert(OID.ROOT, "foo", a, ObjectType.FOLDER),
                insert(OID.ROOT, "bar", b, ObjectType.FOLDER)
        );

        // local change: tmp
        when(mcdb.hasChanges_(sidx)).thenReturn(true);
        ds.createOA_(OA.Type.DIR, sidx, OID.generate(), OID.ROOT, "tmp", t);

        apply(
                rename(OID.ROOT, "tmp", b),
                rename(OID.ROOT, "bar", a),
                rename(OID.ROOT, "foo", b)
        );

        assertHasRemoteLink(a, OID.ROOT, "bar", 4);
        assertHasRemoteLink(b, OID.ROOT, "foo", 5);
        assertTrue(mbdb.isBuffered_(new SOID(sidx, b)));
        assertTrue(mbdb.isBuffered_(new SOID(sidx, a)));

        ac.applyBufferedChanges_(sidx, 5, t);

        mds.expect(rootSID,
                folder("bar", a),
                folder("foo", b),
                folder("tmp"));
        assertIsBuffered(false, a, b);
    }

    @Test
    public void shouldAvoidFalseConflict() throws Exception
    {
        when(mcdb.hasChanges_(sidx)).thenReturn(true);
        mds.create(rootSID,
                folder("Pictures",
                        file("4.jpg"),
                        file("5.jpg"),
                        file("6.jpg")));

        OID p = OID.generate();
        apply(
                insert(OID.ROOT, "Pictures", p, ObjectType.FOLDER),
                insert(p, "1.jpg", OID.generate(), ObjectType.FILE),
                insert(p, "2.jpg", OID.generate(), ObjectType.FILE),
                insert(p, "3.jpg", OID.generate(), ObjectType.FILE),
                rename(OID.ROOT, "Old Pictures", p)
        );
        ac.applyBufferedChanges_(sidx, 5, t);

        assertOAEquals(p, OID.ROOT, "Old Pictures", OA.Type.DIR);

        mds.expect(rootSID,
                folder("Old Pictures", p,
                        file("1.jpg"),
                        file("2.jpg"),
                        file("3.jpg")),
                folder("Pictures",
                        file("4.jpg"),
                        file("5.jpg"),
                        file("6.jpg")));
    }
}
