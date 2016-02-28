/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.mock.logical.LogicalObjectsPrinter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase.MetaChange;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase.RemoteContent;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import org.junit.Test;

import java.sql.SQLException;

import static com.aerofs.daemon.core.polaris.InMemoryDS.*;
import static com.aerofs.daemon.core.polaris.api.RemoteChange.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// TODO: more name conflict tests
public class TestApplyChange extends AbstractTestApplyChange
{
    static final byte[] EMPTY = {};
    static final ContentHash H = new ContentHash(BaseSecUtil.hash());

    private void addMetaChange(SIndex sidx) throws SQLException {
        try (Trans t = tm.begin_()) {
            mcdb.insertChange_(sidx, OID.generate(), OID.generate(), "dummy", t);
            t.commit_();
        }
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
        addMetaChange(sidx);

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
    public void shouldNotBufferInsertWhenObjectLocallyPresent() throws Exception
    {
        OID oid = OID.generate();
        try (Trans t = tm.begin_()) {
            ds.createOA_(Type.DIR, sidx, oid, OID.ROOT, "foo", t);
            t.commit_();
        }

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER)
        );

        // verify
        mds.expect(rootSID,
                folder("foo"));
        assertHasRemoteLink(oid, OID.ROOT, "foo", 1);
        assertIsBuffered(false, oid);
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
        addMetaChange(sidx);

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
        addMetaChange(sidx);

        OID gp = OID.generate(), p = OID.generate(), a = OID.generate(), b = OID.generate();

        apply(
                insert(OID.ROOT, "foo", gp, ObjectType.FOLDER),
                insert(gp, "bar", p, ObjectType.FOLDER),
                insert(p, "baz", a, ObjectType.FILE),
                insert(p, "qux", b, ObjectType.FILE)
        );
        ac.applyBufferedChanges_(sidx, 42);

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
    public void shouldHandleMoveOfBufferedObject() throws Exception
    {
        addMetaChange(sidx);

        OID p = OID.generate(), c = OID.generate();

        apply(
                insert(OID.ROOT, "foo", p, ObjectType.FOLDER),
                insert(p, "bar", c, ObjectType.FOLDER),
                insert(OID.ROOT, "baz", c, ObjectType.FOLDER),
                remove(p, c)
        );
        ac.applyBufferedChanges_(sidx, 42);

        // verify
        mds.expect(rootSID, folder("foo", p), folder("baz", c));
        assertHasRemoteLink(p, OID.ROOT, "foo", 1);
        assertHasRemoteLink(c, OID.ROOT, "baz", 3);
        assertIsBuffered(false, p, c);
    }

    @Test
    public void shouldHandleRenameOfBufferedObject() throws Exception
    {
        addMetaChange(sidx);

        OID p = OID.generate(), c = OID.generate();

        apply(
                insert(OID.ROOT, "foo", p, ObjectType.FOLDER),
                insert(p, "bar", c, ObjectType.FOLDER),
                rename(p, "baz", c)
        );
        ac.applyBufferedChanges_(sidx, 42);

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
        addMetaChange(sidx);

        OID p = OID.generate(), c = OID.generate();

        apply(
                insert(OID.ROOT, "foo", p, ObjectType.FOLDER),
                insert(p, "bar", c, ObjectType.FOLDER),
                remove(p, c)
        );
        ac.applyBufferedChanges_(sidx, 42);

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
        try (Trans t = tm.begin_()) {
            ds.createOA_(OA.Type.DIR, sidx, oid, OID.ROOT, "foo", t);
            rldb.insertParent_(sidx, oid, OID.ROOT, "foo", state.changes.size(), t);
            t.commit_();
        }
        state.get(sidx).put(OID.ROOT, 1L);

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
        try (Trans t = tm.begin_()) {
            rldb.insertParent_(sidx, oid, OID.ROOT, "foo", state.changes.size(), t);
            mbdb.insert_(sidx, oid, OA.Type.DIR, null, 42, t);
            t.commit_();
        }
        state.get(sidx).put(OID.ROOT, 1L);

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
        addMetaChange(sidx);

        OID oid = OID.generate();

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER)
        );
        ac.applyBufferedChanges_(sidx, 0);

        // verify
        assertNotPresent(oid);
        assertNull(ds.getChild_(sidx, OID.ROOT, "bar"));
        assertHasRemoteLink(oid, OID.ROOT, "foo", 1);
        assertIsBuffered(true, oid);
    }

    @Test
    public void shouldApplyBufferedChange() throws Exception
    {
        addMetaChange(sidx);

        OID oid = OID.generate();

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER),
                rename(OID.ROOT, "bar", oid)
        );
        ac.applyBufferedChanges_(sidx, 42);

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
        try (Trans t = tm.begin_()) {
            rldb.insertParent_(sidx, oid, OID.ROOT, "foo", state.changes.size(), t);
            ds.createOA_(OA.Type.DIR, sidx, oid, OID.ROOT, "foo", t);
            t.commit_();
        }
        state.get(sidx).put(OID.ROOT, 1L);

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
        addMetaChange(sidx);

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
        addMetaChange(sidx);

        OID oid = OID.generate();

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER),
                remove(OID.ROOT, oid)
        );
        ac.applyBufferedChanges_(sidx, 42);

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
        addMetaChange(sidx);
        OID alias = OID.generate();
        try (Trans t = tm.begin_()) {
            ds.createOA_(OA.Type.DIR, sidx, alias, OID.ROOT, "foo", t);
            t.commit_();
        }

        OID oid = OID.generate();

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER)
        );
        ac.applyBufferedChanges_(sidx, 42);

        assertNotPresent(alias);
        mds.expect(rootSID,
                folder("foo", oid));
    }

    @Test
    public void shouldAliasFileWithNoContent() throws Exception
    {
        addMetaChange(sidx);
        OID alias = OID.generate();
        try (Trans t = tm.begin_()) {
            ds.createOA_(OA.Type.FILE, sidx, alias, OID.ROOT, "foo", t);
            t.commit_();
        }

        DID did = DID.generate();
        OID oid = OID.generate();
        ContentHash h = new ContentHash(BaseSecUtil.hash());

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FILE),
                updateContent(oid, did, h, 0L, 42L)
        );
        ac.applyBufferedChanges_(sidx, 42);

        assertNotPresent(alias);
        mds.expect(rootSID,
                file("foo", oid));
        assertHasContentChanges(sidx);
        assertNull(cvdb.getVersion_(sidx, oid));
        assertNull(cvdb.getVersion_(sidx, alias));
        assertHasRemoteContent(sidx, oid, new RemoteContent(1, did, h, 0));
    }

    @Test
    public void shouldAliasFileWithSameContent() throws Exception
    {
        ContentHash h = new ContentHash(BaseSecUtil.hash());

        addMetaChange(sidx);
        OID alias = OID.generate();
        try (Trans t = tm.begin_()) {
            ds.createOA_(OA.Type.FILE, sidx, alias, OID.ROOT, "foo", t);
            ds.createCA_(new SOID(sidx, alias), KIndex.MASTER, t);
            ds.setCA_(new SOKID(sidx, alias, KIndex.MASTER), 0L, 42L, h, t);
            ccdb.insertChange_(sidx, alias, t);
            t.commit_();
        }

        DID did = DID.generate();
        OID oid = OID.generate();

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FILE),
                updateContent(oid, did, h, 0L, 42L)
        );
        ac.applyBufferedChanges_(sidx, 42);

        assertNotPresent(alias);
        mds.expect(rootSID,
                file("foo", oid,
                        content(new byte[]{}, 42L)));
        assertHasContentChanges(sidx);
        assertEquals(Long.valueOf(1), cvdb.getVersion_(sidx, oid));
        assertNull(cvdb.getVersion_(sidx, alias));
        assertHasRemoteContent(sidx, oid, new RemoteContent(1, did, h, 0));
    }

    @Test
    public void shouldAliasFileWithDiffContent() throws Exception
    {
        ContentHash h0 = new ContentHash(BaseSecUtil.hash());
        ContentHash h1 = new ContentHash(BaseSecUtil.hash(new byte[]{1}));

        addMetaChange(sidx);
        OID alias = OID.generate();
        try (Trans t = tm.begin_()) {
            ds.createOA_(OA.Type.FILE, sidx, alias, OID.ROOT, "foo", t);
            ds.createCA_(new SOID(sidx, alias), KIndex.MASTER, t);
            ds.setCA_(new SOKID(sidx, alias, KIndex.MASTER), 1L, 42L, h1, t);
            ccdb.insertChange_(sidx, alias, t);
            t.commit_();
        }

        DID did = DID.generate();
        OID oid = OID.generate();

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FILE),
                updateContent(oid, did, h0, 0L, 42L)
        );
        ac.applyBufferedChanges_(sidx, 42);

        assertNotPresent(alias);
        mds.expect(rootSID,
                file("foo", oid,
                        content(new byte[]{1}, 42L)));
        assertHasContentChanges(sidx, oid);
        assertNull(cvdb.getVersion_(sidx, oid));
        assertNull(cvdb.getVersion_(sidx, alias));
        assertHasRemoteContent(sidx, oid, new RemoteContent(1, did, h0, 0));
    }

    @Test
    public void shouldAliasHierarchy() throws Exception
    {
        addMetaChange(sidx);
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

        ac.applyBufferedChanges_(sidx, 42);

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
    public void shouldHandleMoveUnderAlias() throws Exception
    {
        OID p = OID.generate(), lp = OID.generate();
        OID c = SID.folderOID2convertedAnchorOID(OID.generate());

        apply(
                insert(OID.ROOT, "bar", c, ObjectType.STORE)
        );

        try (Trans t = tm.begin_()) {
            ds.createOA_(Type.DIR, sidx, lp, OID.ROOT, "foo", t);
            om.moveInSameStore_(new SOID(sidx, c), lp, "bar", PhysicalOp.MAP, true, t);
            t.commit_();
        }

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        apply(
                insert(OID.ROOT, "foo", p, ObjectType.FOLDER),
                insert(p, "bar", c, ObjectType.STORE),
                remove(OID.ROOT, c)
        );
        ac.applyBufferedChanges_(sidx, 42);

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // verify
        mds.expect(rootSID,
                folder("foo", p,
                        anchor("bar", SID.anchorOID2folderOID(c))));

        assertNotPresent(lp);
        assertHasRemoteLink(p, OID.ROOT, "foo", 2);
        assertHasRemoteLink(c, p, "bar", 3);
        assertIsBuffered(false, p, lp, c);
        assertEquals(new SOID(sidx, p), a2t.dereferenceAliasedOID_(new SOID(sidx, lp)));
    }

    @Test
    public void shouldHandleBufferedNop() throws Exception
    {
        addMetaChange(sidx);
        OID oid = OID.generate();
        try (Trans t = tm.begin_()) {
            ds.createOA_(Type.DIR, sidx, oid, OID.ROOT, "foo", t);
            t.commit_();
        }

        apply(
                insert(OID.ROOT, "foo", oid, ObjectType.FOLDER)
        );
        ac.applyBufferedChanges_(sidx, 42);

        verify(ps, never()).newFolder_(ds.resolve_(new SOID(sidx, oid)));

        mds.expect(rootSID,
                folder("foo", oid));
    }

    @Test
    public void shouldRenameLocalObjectKnownToPolaris() throws Exception
    {
        OID bar = OID.generate();
        apply(
                insert(OID.ROOT, "bar", bar, ObjectType.FILE)
        );
        ac.applyBufferedChanges_(sidx, 42);

        OID local = OID.generate();
        try (Trans t = tm.begin_()) {
            om.moveInSameStore_(new SOID(sidx, bar), OID.ROOT, "foo", PhysicalOp.MAP, true, t);
            oc.createMetaForLinker_(Type.FILE, local, new SOID(sidx, OID.ROOT), "bar", t);
            t.commit_();
        }

        OID remote = OID.generate();

        apply(
                insert(OID.ROOT, "foo", remote, ObjectType.FOLDER)
        );
        ac.applyBufferedChanges_(sidx, 42);

        mds.expect(rootSID,
                folder("foo", remote),
                file("foo (2)", bar),
                file("bar", local));

        // conflicting intermediate moves will be corrected by MetaChangeSubmitter
        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, bar, OID.ROOT, "foo"),
                new MetaChange(sidx, -1, local, OID.ROOT, "bar"),
                new MetaChange(sidx, -1, bar, OID.ROOT, "foo (2)"));
    }

    @Test
    public void shouldRenameLocalObjectUnknownToPolaris() throws Exception
    {
        OID local = OID.generate();
        try (Trans t = tm.begin_()) {
            ds.createOA_(OA.Type.FILE, sidx, local, OID.ROOT, "foo", t);
            mcdb.insertChange_(sidx, local, OID.ROOT, "foo", t);
            t.commit_();
        }

        OID remote = OID.generate();

        apply(
                insert(OID.ROOT, "foo", remote, ObjectType.FOLDER)
        );
        ac.applyBufferedChanges_(sidx, 42);

        mds.expect(rootSID,
                folder("foo", remote),
                file("foo (2)", local));

        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, local, OID.ROOT, "foo"),
                new MetaChange(sidx, -1, local, OID.ROOT, "foo (2)"));
    }

    @Test
    public void shouldRevertLocalRename() throws Exception
    {
        OID foo = OID.generate();
        OID bar = OID.generate();
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(OID.ROOT, "bar", bar, ObjectType.FOLDER)
        );
        ac.applyBufferedChanges_(sidx, 1);

        mds.expect(rootSID,
                folder("foo", foo),
                folder("bar", bar));

        try (Trans t = tm.begin_()) {
            om.moveInSameStore_(new SOID(sidx, foo), OID.ROOT, "baz", PhysicalOp.MAP, true, t);
            t.commit_();
        }

        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, foo, OID.ROOT, "baz"));

        apply(
                rename(OID.ROOT, "baz", bar)
        );
        ac.applyBufferedChanges_(sidx, 42);

        mds.expect(rootSID,
                folder("foo", foo),
                folder("baz", bar));

        // local meta change discarded
        assertHasLocalChanges(sidx);
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
        try (Trans t = tm.begin_()) {
            ds.createOA_(OA.Type.DIR, sidx, local, OID.ROOT, "foo", t);
            rldb.insertParent_(sidx, local, OID.ROOT, "foo", 2, t);
            t.commit_();
        }

        OID remote = OID.generate();

        apply(
                insert(OID.ROOT, "foo", remote, ObjectType.FOLDER)
        );
        ac.applyBufferedChanges_(sidx, 1);

        mds.expect(rootSID,
                folder("foo (2)", remote),
                folder("foo", local));

        assertHasLocalChanges(sidx);
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
        addMetaChange(sidx);
        try (Trans t = tm.begin_()) {
            ds.createOA_(OA.Type.DIR, sidx, OID.generate(), OID.ROOT, "tmp", t);
            t.commit_();
        }

        apply(
                rename(OID.ROOT, "tmp", b),
                rename(OID.ROOT, "bar", a),
                rename(OID.ROOT, "foo", b)
        );

        assertHasRemoteLink(a, OID.ROOT, "bar", 4);
        assertHasRemoteLink(b, OID.ROOT, "foo", 5);
        assertTrue(mbdb.isBuffered_(new SOID(sidx, b)));
        assertTrue(mbdb.isBuffered_(new SOID(sidx, a)));

        ac.applyBufferedChanges_(sidx, 5);

        mds.expect(rootSID,
                folder("bar", a),
                folder("foo", b),
                folder("tmp"));
        assertIsBuffered(false, a, b);
    }

    @Test
    public void shouldAvoidFalseConflict() throws Exception
    {
        addMetaChange(sidx);
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
        ac.applyBufferedChanges_(sidx, 5);

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

    @Test
    public void shouldDetectContentConflict() throws Exception
    {
        OID oid = OID.generate();
        ContentHash h = new ContentHash(BaseSecUtil.hash());
        mds.create(rootSID, file("foo", oid, content(3L, 42, h)));
        try (Trans t = tm.begin_()) {
            rldb.insertParent_(sidx, oid, OID.ROOT, "foo", 0L, t);
            ccdb.insertChange_(sidx, oid, t);
            t.commit_();
        }

        assertNull(cvdb.getVersion_(sidx, oid));
        assertEquals(h, ds.getCAHash_(new SOKID(sidx, oid, KIndex.MASTER)));

        apply(
                updateContent(oid, new ContentHash(BaseSecUtil.hash(new byte[]{(byte) 'a'})), 3L, 0L)
        );

        assertTrue(ccdb.hasChange_(sidx, oid));
        assertNull(cvdb.getVersion_(sidx, oid));
        assertTrue(rcdb.hasRemoteChanges_(sidx, oid, 0L));
    }

    @Test
    public void shouldAvoidFalseContentConflict() throws Exception
    {
        OID oid = OID.generate();
        mds.create(rootSID, file("foo", oid, content(3L, 42, H)));
        try (Trans t = tm.begin_()) {
            rldb.insertParent_(sidx, oid, OID.ROOT, "foo", 0L, t);
            ccdb.insertChange_(sidx, oid, t);
            t.commit_();
        }

        assertNull(cvdb.getVersion_(sidx, oid));
        assertEquals(H, ds.getCAHash_(new SOKID(sidx, oid, KIndex.MASTER)));

        apply(
                updateContent(oid, H, 3L, 0L)
        );

        assertFalse(ccdb.hasChange_(sidx, oid));
        assertEquals((Long) 1L, cvdb.getVersion_(sidx, oid));
        assertTrue(rcdb.hasRemoteChanges_(sidx, oid, 0L));
        assertEquals(H, ds.getCAHash_(new SOKID(sidx, oid, KIndex.MASTER)));
    }

    @Test
    public void shouldResolveCycleA() throws Exception {
        OID foo = OID.generate();
        OID bar = OID.generate();
        OID baz = OID.generate();
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(OID.ROOT, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FOLDER)
        );

        try (Trans t = tm.begin_()) {
            om.moveInSameStore_(new SOID(sidx, foo), baz, "moved", PhysicalOp.MAP, true, t);
            t.commit_();
        }

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        apply(
                insert(foo, "moved", bar, ObjectType.FOLDER),
                remove(OID.ROOT, bar)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        mds.expect(rootSID,
                folder("foo", foo,
                        folder("moved", bar,
                                folder("baz", baz))));
    }

    @Test
    public void shouldResolveCycleB() throws Exception {
        OID foo = OID.generate();
        OID bar = OID.generate();
        OID baz = OID.generate();
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(OID.ROOT, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FOLDER)
        );

        try (Trans t = tm.begin_()) {
            om.moveInSameStore_(new SOID(sidx, bar), foo, "moved", PhysicalOp.MAP, true, t);
            t.commit_();
        }

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        apply(
                insert(baz, "moved", foo, ObjectType.FOLDER),
                remove(OID.ROOT, foo)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        mds.expect(rootSID,
                folder("bar", bar,
                        folder("baz", baz,
                                folder("moved", foo))));
    }
}
