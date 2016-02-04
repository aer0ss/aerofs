package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.expel.LogicalStagingArea;
import com.aerofs.daemon.core.mock.logical.LogicalObjectsPrinter;
import com.aerofs.daemon.core.multiplicity.singleuser.SharedFolderUpdateQueueDatabase;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase.MetaChange;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase.RemoteContent;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.ExpulsionDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.labeling.L;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import org.junit.Test;

import java.util.Properties;

import static com.aerofs.daemon.core.polaris.InMemoryDS.*;
import static com.aerofs.daemon.core.polaris.api.RemoteChange.*;
import static org.junit.Assert.*;

// TODO: more tests, including:
// - aliasing
// - buffering
//
// TODO: verify state of physical fs (especially NRO)
// TODO: verify state of content fetch queue
//
// TODO: related tests
//   - cross-store move through ImmigrantCreator
//   - create+delete sequence before meta submit
public class TestApplyChange_Share extends AbstractTestApplyChange {
    OID foo = OID.generate();
    OID bar = OID.generate();
    OID baz = OID.generate();
    OID qux = OID.generate();

    DID did = DID.generate();
    static final ContentHash h = new ContentHash(BaseSecUtil.hash());

    static final byte[] EMPTY = {};
    static final byte[] CONTENT = {'d', 'e', 'a', 'd'};

    @Test
    public void shouldShare() throws Exception
    {
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                insert(bar, "qux", qux, ObjectType.FOLDER),
                updateContent(baz, did, h, 0L, 42L)
        );

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(sidx, baz, CONTENT, 1234, t);
            downloadContent(sidx, baz, 1, EMPTY, 42, t);
            t.commit_();
        }

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH),
                folder("foo", foo,
                        folder("bar", bar,
                                file("baz", baz,
                                        content(CONTENT, 1234),
                                        content(EMPTY, 42)),
                                folder("qux", qux))));

        apply(
                share(foo)
        );

        SIndex shared = mds.sm.get_(SID.folderOID2convertedStoreSID(foo));

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // verify
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH),
                        folder("bar", bar,
                                file("baz", baz,
                                        content(CONTENT, 1234),
                                        content(EMPTY, 42)),
                                folder("qux", qux))));

        // shared objects do not exist in root store anymore
        assertNotPresent(bar, baz);
        assertHasRemoteLink(SID.folderOID2convertedAnchorOID(foo), OID.ROOT, "foo", 6);

        // remote links in new store with negative version
        assertHasRemoteLink(shared, bar, OID.ROOT, "bar", -1);
        assertHasRemoteLink(shared, baz, bar, "baz", -1);

        // no local changes
        assertHasLocalChanges(sidx);
        assertHasContentChanges(sidx);
        assertHasLocalChanges(shared);
        assertHasContentChanges(shared, baz);
        assertHasRemoteContent(shared, baz, new RemoteContent(1, did, h, 0));

        assertEquals(Long.valueOf(1), cvdb.getVersion_(shared, baz));

        // transform in dest store for convergence
        state.preShare(sidx, shared, foo, bar, baz);
        apply(shared,
                insert(OID.ROOT, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                insert(bar, "qux", qux, ObjectType.FOLDER),
                updateContent(baz, did, h, 0L, 42L)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // verify again
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                anchor("foo", foo,
                        folder("bar", bar,
                                file("baz", baz,
                                        content(CONTENT, 1234),
                                        content(EMPTY, 42)),
                                folder("qux", qux))));

        // no local changes
        assertHasLocalChanges(sidx);
        assertHasContentChanges(sidx);

        assertHasLocalChanges(shared);
        assertHasContentChanges(shared, baz);
        assertHasRemoteContent(shared, baz, new RemoteContent(1, did, h, 0));

        // TODO: more state checks?
    }

    @Test
    public void shouldShareAndMoveOut() throws Exception
    {
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L),
                insert(OID.ROOT, "qux", qux, ObjectType.FOLDER)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        assertHasRemoteContent(sidx, baz, new RemoteContent(1, did, h, 0));

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(sidx, baz, CONTENT, 1234, t);
            downloadContent(sidx, baz, 1,EMPTY, 42, t);

            // local change
            om.moveInSameStore_(new SOID(sidx, bar), qux, "moved", PhysicalOp.MAP, true, t);
            t.commit_();
        }

        apply(
                share(foo)
        );
        SIndex shared = mds.sm.get_(SID.folderOID2convertedStoreSID(foo));

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // verify
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH,
                                folder(bar.toStringFormal(), bar,
                                        file("baz", baz)))),
                folder("qux", qux,
                        folder("moved",
                                file("baz", null,
                                        content(CONTENT, 1234),
                                        content(EMPTY, 42)))));

        assertHasRemoteLink(SID.folderOID2convertedAnchorOID(foo), OID.ROOT, "foo", 6);

        // remote links in new store with negative version
        assertHasRemoteLink(shared, bar, OID.ROOT, "bar", -1);
        assertHasRemoteLink(shared, baz, bar, "baz", -1);

        // local changes to account for subtree extraction
        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, oidAt("qux/moved"), qux, "moved"),
                new MetaChange(sidx, -1, oidAt("qux/moved/baz"), oidAt("qux/moved"), "baz"));
        assertHasLocalChanges(shared,
                new MetaChange(shared, -1, bar, OID.TRASH, bar.toStringFormal()));

        assertHasContentChanges(shared);
        assertHasContentChanges(sidx, oidAt("qux/moved/baz"));

        assertHasRemoteContent(shared, baz, new RemoteContent(1, did, h, 0));
        assertHasRemoteContent(sidx, oidAt("qux/moved/baz"));

        assertEquals(Long.valueOf(1), cvdb.getVersion_(shared, baz));
        assertNull(cvdb.getVersion_(sidx, oidAt("qux/moved/baz")));

        // transform in dest store for convergence

        state.preShare(sidx, shared, foo, bar, baz);
        apply(shared,
                insert(OID.ROOT, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // verify again
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH,
                                folder(bar.toStringFormal(), bar,
                                        file("baz", baz)))),
                folder("qux", qux,
                        folder("moved",
                                file("baz", null,
                                        content(CONTENT, 1234),
                                        content(0, 42, h)))));

        // remote links updated
        assertHasRemoteLink(shared, bar, OID.ROOT, "bar", 7);
        assertHasRemoteLink(shared, baz, bar, "baz", 8);

        // local changes preserved
        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, oidAt("qux/moved"), qux, "moved"),
                new MetaChange(sidx, -1, oidAt("qux/moved/baz"), oidAt("qux/moved"), "baz"));
        assertHasLocalChanges(shared,
                new MetaChange(shared, -1, bar, OID.TRASH, bar.toStringFormal()));

        assertHasContentChanges(shared);
        assertHasContentChanges(sidx, oidAt("qux/moved/baz"));

        assertEquals(Long.valueOf(1), cvdb.getVersion_(shared, baz));
        assertNull(cvdb.getVersion_(sidx, oidAt("qux/moved/baz")));

        assertHasRemoteContent(shared, baz, new RemoteContent(1, did, h, 0));
        assertHasRemoteContent(sidx, oidAt("qux/moved/baz"));

        assertHasContentChanges(sidx, oidAt("qux/moved/baz"));
    }

    @Test
    public void shouldShareAndMoveIn() throws Exception
    {
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(OID.ROOT, "qux", qux, ObjectType.FOLDER),
                insert(qux, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        assertHasRemoteContent(sidx, baz, new RemoteContent(1, did, h, 0));

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(sidx, baz, CONTENT, 1234, t);
            downloadContent(sidx, baz, 1, EMPTY, 42, t);

            // content change before cross-store move
            ccdb.insertChange_(sidx, baz, t);

            // local change
            om.moveInSameStore_(new SOID(sidx, bar), foo, "moved", PhysicalOp.MAP, true, t);
            t.commit_();
        }

        apply(
                share(foo)
        );
        SIndex shared = mds.sm.get_(SID.folderOID2convertedStoreSID(foo));

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // verify
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo),
                        folder(bar.toStringFormal(), bar,
                                file("baz", baz))),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH),
                        folder("moved",
                                file("baz", null,
                                        content(CONTENT, 1234),
                                        content(0, 42, h)))),
                folder("qux", qux));

        assertHasRemoteLink(SID.folderOID2convertedAnchorOID(foo), OID.ROOT, "foo", 6);

        // local changes to account for subtree migration
        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, bar, OID.TRASH, bar.toStringFormal()));
        assertHasLocalChanges(shared,
                new MetaChange(shared, -1, oidAt("foo/moved"), OID.ROOT, "moved"),
                new MetaChange(shared, -1, oidAt("foo/moved/baz"), oidAt("foo/moved"), "baz"));

        assertHasContentChanges(sidx);
        assertHasContentChanges(shared, oidAt("foo/moved/baz"));

        assertEquals(Long.valueOf(1), cvdb.getVersion_(sidx, baz));
        assertNull(cvdb.getVersion_(shared, oidAt("foo/moved/baz")));

        assertHasRemoteContent(sidx, baz, new RemoteContent(1, did, h, 0));
        assertHasRemoteContent(shared, oidAt("foo/moved/baz"));

        assertHasContentChanges(shared, oidAt("foo/moved/baz"));

        // TODO: more state checks?
    }

    @Test
    public void shouldPreserveInserts() throws Exception
    {
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER)
        );

        // local change
        try (Trans t = tm.begin_()) {
            oc.createMetaForLinker_(Type.DIR, bar, new SOID(sidx, foo), "bar", t);
            oc.createMetaForLinker_(Type.FILE, baz, new SOID(sidx, bar), "baz", t);
            ds.createCA_(new SOID(sidx, baz), KIndex.MASTER, t);
            ds.setCA_(new SOKID(sidx, baz, KIndex.MASTER), 0, 42, h, t);
            ccdb.insertChange_(sidx, baz, t);
            t.commit_();
        }

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, bar, foo, "bar"),
                new MetaChange(sidx, -1, baz, bar, "baz"));

        assertHasContentChanges(sidx, baz);

        apply(
                share(foo)
        );

        SIndex shared = mds.sm.get_(SID.folderOID2convertedStoreSID(foo));

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // verify
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH),
                        folder("bar", bar,
                                file("baz", baz,
                                        content(EMPTY, 42)))));

        // shared objects do not exist in root store anymore
        assertNotPresent(bar, baz);
        assertHasRemoteLink(SID.folderOID2convertedAnchorOID(foo), OID.ROOT, "foo", 2);

        // preserve local changes
        assertHasLocalChanges(sidx);
        assertHasLocalChanges(shared,
                new MetaChange(shared, -1, bar, OID.ROOT, "bar"),
                new MetaChange(shared, -1, baz, bar, "baz"));

        assertHasContentChanges(sidx);
        assertHasContentChanges(shared, baz);

        assertNull(cvdb.getVersion_(shared, baz));
        // TODO: more state checks?
    }

    @Test
    public void shouldPreserveMove() throws Exception
    {
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FOLDER),
                insert(baz, "qux", qux, ObjectType.FILE),
                updateContent(qux, did, h, 0L, 42L)
        );

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(sidx, qux, CONTENT, 1234, t);
            downloadContent(sidx, qux, 1, EMPTY, 42, t);

            // local change
            om.moveInSameStore_(new SOID(sidx, foo), OID.ROOT, "oof", PhysicalOp.MAP, true, t);
            om.moveInSameStore_(new SOID(sidx, bar), foo, "rab", PhysicalOp.MAP, true, t);
            om.moveInSameStore_(new SOID(sidx, qux), bar, "qux", PhysicalOp.MAP, true, t);
            t.commit_();
        }

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, foo, OID.ROOT, "oof"),
                new MetaChange(sidx, -1, bar, foo, "rab"),
                new MetaChange(sidx, -1, qux, bar, "qux"));

        assertHasContentChanges(sidx, qux);

        assertHasRemoteContent(sidx, qux, new RemoteContent(1, did, h, 0));

        apply(
                share(foo)
        );

        SIndex shared = mds.sm.get_(SID.folderOID2convertedStoreSID(foo));

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // verify
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                anchor("oof", foo,
                        folder(LibParam.TRASH, OID.TRASH),
                        folder("rab", bar,
                                folder("baz", baz),
                                file("qux", qux,
                                        content(CONTENT, 1234),
                                        content(EMPTY, 42)))));

        // shared objects do not exist in root store anymore
        assertNotPresent(bar, baz);
        assertHasRemoteLink(SID.folderOID2convertedAnchorOID(foo), OID.ROOT, "foo", 6);

        // preserve local changes
        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, SID.folderOID2convertedAnchorOID(foo), OID.ROOT, "oof"));
        assertHasLocalChanges(shared,
                new MetaChange(shared, -1, bar, OID.ROOT, "rab"),
                new MetaChange(shared, -1, qux, bar, "qux"));

        assertHasContentChanges(sidx);
        assertHasContentChanges(shared, qux);

        assertHasRemoteContent(shared, qux, new RemoteContent(1, did, h, 0));

        assertEquals(Long.valueOf(1), cvdb.getVersion_(shared, qux));
        // TODO: more state checks?
    }

    @Test
    public void shouldPreserveDelete() throws Exception
    {
        OID quux = OID.generate();
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L),
                insert(OID.ROOT, "qux", qux, ObjectType.FOLDER),
                insert(qux, "quux", quux, ObjectType.FILE)
        );

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(sidx, baz, CONTENT, 1234, t);
            downloadContent(sidx, baz, 1, EMPTY, 42, t);

            // local change
            om.moveInSameStore_(new SOID(sidx, qux), bar, "qux", PhysicalOp.MAP, true, t);
            od.delete_(new SOID(sidx, bar), PhysicalOp.MAP, t);
            inj.getInstance(LogicalStagingArea.class).ensureStoreClean_(sidx, t);
            t.commit_();
        }

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, qux, bar, "qux"),
                new MetaChange(sidx, -1, bar, OID.TRASH, bar.toStringFormal()));

        assertHasContentChanges(sidx);

        assertHasRemoteContent(sidx, baz, new RemoteContent(1, did, h, 0));
        assertHasRemoteContent(sidx, quux);

        apply(
                share(foo)
        );

        SIndex shared = mds.sm.get_(SID.folderOID2convertedStoreSID(foo));

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // verify
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo),
                        folder(qux.toStringFormal(), qux,
                                file("quux", quux))),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH,
                                folder(bar.toStringFormal(), bar,
                                        file("baz", baz)))));

        // shared objects do not exist in root store anymore
        assertNotPresent(bar, baz);
        assertAliased(sidx, bar, OID.TRASH);
        assertAliased(sidx, baz, baz);
        assertHasRemoteLink(SID.folderOID2convertedAnchorOID(foo), OID.ROOT, "foo", 7);

        // preserve local changes
        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, qux, bar, "qux")); // NB: aliased
        assertHasLocalChanges(shared,
                new MetaChange(shared, -1, bar, OID.TRASH, bar.toStringFormal()));

        assertHasContentChanges(sidx);
        assertHasContentChanges(shared);

        assertHasRemoteContent(shared, baz, new RemoteContent(1, did, h, 0));

        assertEquals(Long.valueOf(1), cvdb.getVersion_(shared, baz));
        // TODO: more state checks?
    }

    @Test
    public void shouldPreserveExpulsion() throws Exception {
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L)
        );

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(sidx, baz, CONTENT, 1234, t);
            downloadContent(sidx, baz, 1, EMPTY, 42, t);

            // local change
            inj.getInstance(Expulsion.class).setExpelled_(true, new SOID(sidx, bar), t);
            t.commit_();
        }

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        apply(
                share(foo)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        SIndex shared = mds.sm.get_(SID.folderOID2convertedStoreSID(foo));

        // verify
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH),
                        folder("bar", bar,
                                file("baz", baz))));

        assertNotPresent(sidx, bar, baz);
        assertTrue(ds.getOA_(new SOID(shared, bar)).isSelfExpelled());
        assertTrue(ds.getOA_(new SOID(shared, bar)).isExpelled());
        assertFalse(ds.getOA_(new SOID(shared, baz)).isSelfExpelled());
        assertTrue(ds.getOA_(new SOID(shared, baz)).isExpelled());

        try (IDBIterator<SOID> it = inj.getInstance(ExpulsionDatabase.class).getExpelledObjects_()) {
            assertTrue(it.next_());
            assertTrue(it.get_().equals(new SOID(shared, bar)));
            assertFalse(it.next_());
        }
        assertHasRemoteLink(SID.folderOID2convertedAnchorOID(foo), OID.ROOT, "foo", 5);

        assertHasContentChanges(sidx);
        assertHasContentChanges(shared);

        assertHasRemoteContent(shared, baz, new RemoteContent(1, did, h, 0));

        assertEquals(Long.valueOf(1), cvdb.getVersion_(shared, baz));
    }

    @Test
    public void shouldShareExpelledFolder() throws Exception {
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L)
        );

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(sidx, baz, CONTENT, 1234, t);
            downloadContent(sidx, baz, 1, EMPTY, 42, t);

            // local change
            inj.getInstance(Expulsion.class).setExpelled_(true, new SOID(sidx, foo), t);
            t.commit_();
        }

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        apply(
                share(foo)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // verify
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                anchor("foo", foo));

        OID anchor = SID.folderOID2convertedAnchorOID(foo);
        // store should be expelled
        assertNull(mds.sm.getNullable_(SID.folderOID2convertedStoreSID(foo)));
        assertTrue(ds.getOA_(new SOID(sidx, anchor)).isSelfExpelled());
        assertTrue(ds.getOA_(new SOID(sidx, anchor)).isExpelled());
        try (IDBIterator<SOID> it = inj.getInstance(ExpulsionDatabase.class).getExpelledObjects_()) {
            assertTrue(it.next_());
            assertTrue(it.get_().equals(new SOID(sidx, anchor)));
            assertFalse(it.next_());
        }
        assertHasRemoteLink(anchor, OID.ROOT, "foo", 5);

        assertHasContentChanges(sidx);
    }

    @Test
    public void shouldShareDeletedFolder() throws Exception {
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L)
        );

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(sidx, baz, CONTENT, 1234, t);
            downloadContent(sidx, baz, 1, EMPTY, 42, t);

            // local change
            od.delete_(new SOID(sidx, foo), PhysicalOp.MAP, t);
            inj.getInstance(LogicalStagingArea.class).ensureStoreClean_(sidx, t);
            t.commit_();
        }

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        apply(
                share(foo)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        OID anchor = SID.folderOID2convertedAnchorOID(foo);

        // verify
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo),
                        anchor(anchor.toStringFormal(), foo)));

        // store should be expelled
        assertNull(mds.sm.getNullable_(SID.folderOID2convertedStoreSID(foo)));
        assertNotPresent(bar, baz);
        assertHasRemoteLink(anchor, OID.ROOT, "foo", 5);

        // NB: the newName is not consistent with the actual name in the OA table but that's
        // not an issue for deletion
        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, anchor, OID.TRASH, foo.toStringFormal()));

        // check for entry in leave queue
        // NB: we expect none, as we want to make sure the deletion is accepted before issuing
        // a leave command to sp
        // TODO: similarly delay issuing of leave command in other cases of anchor deletion
        assertFalse(inj.getInstance(SharedFolderUpdateQueueDatabase.class).getCommands_().next_());

        assertHasContentChanges(sidx);
        assertHasRemoteContent(sidx, baz);
    }

    @Test
    public void shouldShareAroundLocallyNestedShare() throws Exception {
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(OID.ROOT, "qux", qux, ObjectType.FOLDER),
                insert(qux, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L)
        );

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(sidx, baz, CONTENT, 1234, t);
            downloadContent(sidx, baz, 1, EMPTY, 42, t);

            // local change
            om.moveInSameStore_(new SOID(sidx, qux), foo, "moved", PhysicalOp.MAP, true, t);
            t.commit_();
        }

        // here comes nested sharing
        apply(
                share(qux),
                share(foo)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        SIndex shared = mds.sm.get_(SID.folderOID2convertedStoreSID(foo));
        OID anchor = SID.folderOID2convertedAnchorOID(foo);
        OID nestedAnchor = SID.folderOID2convertedAnchorOID(qux);

        // verify
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo),
                        folder(qux.toStringFormal(), qux),
                        anchor(nestedAnchor.toStringFormal(), qux)),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH),
                        folder("moved",
                                folder("bar",
                                        file("baz", null,
                                                content(CONTENT, 1234),
                                                content(EMPTY, 42))))));

        assertNull(mds.sm.getNullable_(SID.folderOID2convertedStoreSID(qux)));
        assertNotPresent(sidx, bar, baz);
        assertNotPresent(shared, foo, bar, baz);
        assertHasRemoteLink(nestedAnchor, OID.ROOT, "qux", 6);
        assertHasRemoteLink(anchor, OID.ROOT, "foo", 7);

        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, nestedAnchor, OID.TRASH, nestedAnchor.toStringFormal()));
        assertHasLocalChanges(shared,
                new MetaChange(shared, -1, oidAt("foo/moved"), OID.ROOT, "moved"),
                new MetaChange(shared, -1, oidAt("foo/moved/bar"), oidAt("foo/moved"), "bar"),
                new MetaChange(shared, -1, oidAt("foo/moved/bar/baz"), oidAt("foo/moved/bar"), "baz"));

        assertHasContentChanges(sidx);
        assertHasContentChanges(shared, oidAt("foo/moved/bar/baz"));

        // check for entry in leave queue
        // NB: we expect none, as we want to make sure the deletion is accepted before issuing
        // a leave command to sp
        // TODO: similarly delay issuing of leave command in other cases of anchor deletion
        assertFalse(inj.getInstance(SharedFolderUpdateQueueDatabase.class).getCommands_().next_());
    }

    @Test
    public void shouldShareBeneathLocallyNestedShare() throws Exception {
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(OID.ROOT, "qux", qux, ObjectType.FOLDER),
                insert(qux, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L)
        );

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(sidx, baz, CONTENT, 1234, t);
            downloadContent(sidx, baz, 1, EMPTY, 42, t);

            // local change
            om.moveInSameStore_(new SOID(sidx, qux), foo, "moved", PhysicalOp.MAP, true, t);
            t.commit_();
        }

        // here comes nested sharing
        // NB: this ordering is equivalent to sharing a deleted folder
        apply(
                share(foo),
                share(qux)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        SIndex shared = mds.sm.get_(SID.folderOID2convertedStoreSID(foo));
        OID anchor = SID.folderOID2convertedAnchorOID(foo);
        OID nestedAnchor = SID.folderOID2convertedAnchorOID(qux);

        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo),
                        folder(qux.toStringFormal(), qux),
                        anchor(nestedAnchor.toStringFormal(), qux)),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH),
                        folder("moved",
                                folder("bar",
                                        file("baz", null,
                                                content(CONTENT, 1234),
                                                content(EMPTY, 42))))));

        assertNull(mds.sm.getNullable_(SID.folderOID2convertedStoreSID(qux)));
        assertNotPresent(sidx, bar, baz);
        assertNotPresent(shared, foo, bar, baz);
        assertHasRemoteLink(anchor, OID.ROOT, "foo", 6);
        assertHasRemoteLink(nestedAnchor, OID.ROOT, "qux", 7);

        // NB: the newName is not consistent with the actual name in the OA table but that's
        // not an issue for deletion
        assertHasLocalChanges(sidx,
                new MetaChange(sidx, -1, nestedAnchor, OID.TRASH, qux.toStringFormal()));
        assertHasLocalChanges(shared,
                new MetaChange(shared, -1, oidAt("foo/moved"), OID.ROOT, "moved"),
                new MetaChange(shared, -1, oidAt("foo/moved/bar"), oidAt("foo/moved"), "bar"),
                new MetaChange(shared, -1, oidAt("foo/moved/bar/baz"), oidAt("foo/moved/bar"), "baz"));

        assertHasContentChanges(sidx);
        assertHasContentChanges(shared, oidAt("foo/moved/bar/baz"));

        // check for entry in leave queue
        // NB: we expect none, as we want to make sure the deletion is accepted before issuing
        // a leave command to sp
        // TODO: similarly delay issuing of leave command in other cases of anchor deletion
        assertFalse(inj.getInstance(SharedFolderUpdateQueueDatabase.class).getCommands_().next_());
    }

    @Test
    public void shouldBufferAndDropWhenAnchorPresentWithSameName() throws Exception {
        SID sid = SID.folderOID2convertedStoreSID(foo);

        // restore anchor after unlink
        try (Trans t = tm.begin_()) {
            oc.createMeta_(Type.ANCHOR, new SOID(sidx, SID.folderOID2convertedAnchorOID(foo)),
                    OID.ROOT, "foo", PhysicalOp.MAP, false, false, t);
            t.commit_();
        }

        SIndex shared = mds.sm.get_(sid);

        // sync changes in shared folder
        apply(shared,
                insert(OID.ROOT, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L)
        );

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(shared, baz, CONTENT, 1234, t);
            downloadContent(shared, baz, 1, EMPTY, 42, t);
            t.commit_();
        }

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH),
                        folder("bar", bar,
                                file("baz", baz,
                                        content(CONTENT, 1234),
                                        content(EMPTY, 42)))));

        assertHasRemoteContent(shared, baz,
                new RemoteContent(1, did, new ContentHash(BaseSecUtil.hash(EMPTY)), 0L));

        // sync changes in root folder
        apply(sidx,
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L),
                share(foo),
                insert(OID.ROOT, "qux", qux, ObjectType.FOLDER)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH),
                        folder("bar", bar,
                                file("baz", baz,
                                        content(CONTENT, 1234),
                                        content(EMPTY, 42)))),
                folder("qux", qux));

        assertHasRemoteContent(sidx, baz);
        assertHasRemoteContent(shared, baz,
                new RemoteContent(1, did, new ContentHash(BaseSecUtil.hash(EMPTY)), 0L));
        assertHasContentChanges(shared, baz);
    }

    @Test
    public void shouldBufferAndDropWhenAnchorPresentWithDifferentName() throws Exception {
        SID sid = SID.folderOID2convertedStoreSID(foo);

        // restore anchor after unlink
        try (Trans t = tm.begin_()) {
            oc.createMeta_(Type.ANCHOR, new SOID(sidx, SID.folderOID2convertedAnchorOID(foo)),
                    OID.ROOT, "foolish", PhysicalOp.MAP, false, false, t);
            t.commit_();
        }

        SIndex shared = mds.sm.get_(sid);

        // sync changes in shared folder
        apply(shared,
                insert(OID.ROOT, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L)
        );

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(shared, baz, CONTENT, 1234, t);
            downloadContent(shared, baz, 1, EMPTY, 42, t);
            t.commit_();
        }

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH),
                anchor("foolish", foo,
                        folder(LibParam.TRASH, OID.TRASH),
                        folder("bar", bar,
                                file("baz", baz,
                                        content(CONTENT, 1234),
                                        content(EMPTY, 42)))));

        assertHasRemoteContent(shared, baz,
                new RemoteContent(1, did, new ContentHash(BaseSecUtil.hash(EMPTY)), 0L));

        // sync changes in root folder
        apply(sidx,
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                updateContent(baz, did, h, 0L, 42L),
                share(foo),
                insert(OID.ROOT, "qux", qux, ObjectType.FOLDER)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                anchor("foolish", foo,
                        folder(LibParam.TRASH, OID.TRASH),
                        folder("bar", bar,
                                file("baz", baz,
                                        content(CONTENT, 1234),
                                        content(EMPTY, 42)))),
                folder("qux", qux));

        assertHasRemoteContent(sidx, baz);
        assertHasRemoteContent(shared, baz,
                new RemoteContent(1, did, new ContentHash(BaseSecUtil.hash(EMPTY)), 0L));
        assertHasContentChanges(shared, baz);
    }

    @Test
    public void shouldHandleInsertInOriginalFolder() throws Exception {
        // restore anchor after unlink
        try (Trans t = tm.begin_()) {
            oc.createMeta_(Type.ANCHOR, new SOID(sidx, SID.folderOID2convertedAnchorOID(foo)),
                    OID.ROOT, "foolish", PhysicalOp.MAP, false, false, t);
            oc.createMeta_(Type.ANCHOR, new SOID(sidx, SID.folderOID2convertedAnchorOID(bar)),
                    OID.ROOT, "bargain", PhysicalOp.MAP, false, false, t);
            t.commit_();
        }

        // sync changes in root folder
        apply(sidx,
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "baz", baz, ObjectType.FOLDER),
                insert(OID.ROOT, "bar", bar, ObjectType.FOLDER),
                share(bar),
                insert(foo, "qux", qux, ObjectType.FILE),
                share(foo)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo),
                        folder(bar.toStringFormal(), bar)),
                anchor("foolish", foo,
                        folder(LibParam.TRASH, OID.TRASH)),
                anchor("bargain", bar,
                        folder(LibParam.TRASH, OID.TRASH)));

    }

    @Test
    public void shouldHandleRenameInOriginalFolder() throws Exception {
        // restore anchor after unlink
        try (Trans t = tm.begin_()) {
            oc.createMeta_(Type.ANCHOR, new SOID(sidx, SID.folderOID2convertedAnchorOID(foo)),
                    OID.ROOT, "foolish", PhysicalOp.MAP, false, false, t);
            oc.createMeta_(Type.ANCHOR, new SOID(sidx, SID.folderOID2convertedAnchorOID(bar)),
                    OID.ROOT, "bargain", PhysicalOp.MAP, false, false, t);
            t.commit_();
        }

        // sync changes in root folder
        apply(sidx,
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "baz", baz, ObjectType.FOLDER),
                insert(OID.ROOT, "bar", bar, ObjectType.FOLDER),
                share(bar),
                rename(foo, "bastion", baz),
                share(foo)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo),
                        folder(bar.toStringFormal(), bar)),
                anchor("foolish", foo,
                        folder(LibParam.TRASH, OID.TRASH)),
                anchor("bargain", bar,
                        folder(LibParam.TRASH, OID.TRASH)));

    }

    @Test
    public void shouldHandleMoveIntoOriginalFolder() throws Exception {
        // restore anchor after unlink
        try (Trans t = tm.begin_()) {
            oc.createMeta_(Type.ANCHOR, new SOID(sidx, SID.folderOID2convertedAnchorOID(foo)),
                    OID.ROOT, "foolish", PhysicalOp.MAP, false, false, t);
            oc.createMeta_(Type.ANCHOR, new SOID(sidx, SID.folderOID2convertedAnchorOID(bar)),
                    OID.ROOT, "bargain", PhysicalOp.MAP, false, false, t);
            t.commit_();
        }

        // sync changes in root folder
        apply(sidx,
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(OID.ROOT, "baz", baz, ObjectType.FOLDER),
                insert(OID.ROOT, "bar", bar, ObjectType.FOLDER),
                share(bar),
                insert(foo, "bastion", baz, ObjectType.FOLDER),
                remove(OID.ROOT, baz),
                share(foo)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo),
                        folder(bar.toStringFormal(), bar)),
                anchor("foolish", foo,
                        folder(LibParam.TRASH, OID.TRASH)),
                anchor("bargain", bar,
                        folder(LibParam.TRASH, OID.TRASH)));

    }

    @Test
    public void shouldHandleMoveOutOfOriginalFolder() throws Exception {
        // restore anchor after unlink
        try (Trans t = tm.begin_()) {
            oc.createMeta_(Type.ANCHOR, new SOID(sidx, SID.folderOID2convertedAnchorOID(foo)),
                    OID.ROOT, "foolish", PhysicalOp.MAP, false, false, t);
            oc.createMeta_(Type.ANCHOR, new SOID(sidx, SID.folderOID2convertedAnchorOID(bar)),
                    OID.ROOT, "bargain", PhysicalOp.MAP, false, false, t);
            t.commit_();
        }

        // sync changes in root folder
        apply(sidx,
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "baz", baz, ObjectType.FOLDER),
                insert(OID.ROOT, "bar", bar, ObjectType.FOLDER),
                share(bar),
                insert(OID.ROOT, "bastion", baz, ObjectType.FOLDER),
                remove(foo, baz),
                share(foo)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo),
                        folder(bar.toStringFormal(), bar)),
                anchor("foolish", foo,
                        folder(LibParam.TRASH, OID.TRASH)),
                anchor("bargain", bar,
                        folder(LibParam.TRASH, OID.TRASH)),
                folder("bastion", baz));

    }

    @Test
    public void shouldHandleShareForExistingRoot() throws Exception {
        Properties ts = new Properties();
        ts.setProperty("labeling.isMultiuser", "true");
        L.set(ts);

        try {
            SID sid = SID.folderOID2convertedStoreSID(foo);

            try (Trans t = tm.begin_()) {
                inj.getInstance(StoreCreator.class).createRootStore_(sid, "foo", t);
                t.commit_();
            }

            SIndex shared = mds.sm.get_(sid);

            apply(shared,
                    insert(OID.ROOT, "bar", bar, ObjectType.FOLDER)
            );

            apply(sidx,
                    insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                    insert(foo, "baz", baz, ObjectType.FOLDER),
                    insert(foo, "bar", bar, ObjectType.FOLDER),
                    share(foo)
            );

            LogicalObjectsPrinter.printRecursively(rootSID, ds);

            mds.expect(rootSID,
                    folder(LibParam.TRASH, OID.TRASH,
                            folder(foo.toStringFormal(), foo)),
                    anchor("foo", foo,
                            folder(LibParam.TRASH, OID.TRASH),
                            folder("bar", bar)));
        } finally {
            L.set(new Properties());
        }
    }
}
