package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.daemon.core.mock.logical.LogicalObjectsPrinter;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.id.SIndex;
import org.junit.Ignore;
import org.junit.Test;

import static com.aerofs.daemon.core.polaris.InMemoryDS.*;
import static com.aerofs.daemon.core.polaris.InMemoryDS.content;
import static com.aerofs.daemon.core.polaris.InMemoryDS.folder;
import static com.aerofs.daemon.core.polaris.api.RemoteChange.*;

public class TestApplyChange_Migrate extends AbstractTestApplyChange {
    OID foo = OID.generate();
    OID bar = OID.generate();
    OID baz = OID.generate();
    OID qux = OID.generate();

    DID did = DID.generate();
    static final ContentHash h = new ContentHash(BaseSecUtil.hash());

    static final byte[] EMPTY = {};
    static final byte[] CONTENT = {'d', 'e', 'a', 'd'};

    @Test
    public void shouldMigrateCreateFirst() throws Exception {
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                insert(bar, "qux", qux, ObjectType.FOLDER),
                updateContent(baz, did, h, 0L, 42L),
                share(foo)
        );

        SIndex shared = mds.sm.get_(SID.folderOID2convertedStoreSID(foo));

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(shared, baz, CONTENT, 1234, t);
            downloadContent(shared, baz, 1, EMPTY, 42, t);
            t.commit_();
        }

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

        OID bar2 = OID.generate();
        OID baz2 = OID.generate();
        OID qux2 = OID.generate();
        apply(
                insert(OID.ROOT, "moved", bar2, ObjectType.FOLDER, bar),
                insert(bar2, "baz", baz2, ObjectType.FILE, baz),
                insert(bar2, "qux", qux2, ObjectType.FOLDER, qux),
                updateContent(baz2, did, h, 0L, 42L)
        );

        apply(shared,
                remove(OID.ROOT, bar, bar2)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // verify
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                folder("moved", bar2,
                        file("baz", baz2,
                                content(CONTENT, 1234),
                                content(EMPTY, 42)),
                        folder("qux", qux2)),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH,
                           folder(bar.toStringFormal(), bar,
                                    file("baz", baz),
                                    folder("qux", qux)))));

        assertHasContentChanges(sidx, baz2);
    }

    // FIXME: mocking IPhysicalStorage well enough to restore from history is a pain...
    @Ignore
    @Test
    public void shouldMigrateDeleteFirst() throws Exception {
        apply(
                insert(OID.ROOT, "foo", foo, ObjectType.FOLDER),
                insert(foo, "bar", bar, ObjectType.FOLDER),
                insert(bar, "baz", baz, ObjectType.FILE),
                insert(bar, "qux", qux, ObjectType.FOLDER),
                updateContent(baz, did, h, 0L, 42L),
                share(foo)
        );

        SIndex shared = mds.sm.get_(SID.folderOID2convertedStoreSID(foo));

        try (Trans t = tm.begin_()) {
            // local version + remote dl -> conflict
            setContent(shared, baz, CONTENT, 1234, t);
            downloadContent(shared, baz, 1, EMPTY, 42, t);
            t.commit_();
        }

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

        OID bar2 = OID.generate();
        OID baz2 = OID.generate();
        OID qux2 = OID.generate();

        apply(shared,
                remove(OID.ROOT, bar, bar2)
        );

        apply(
                insert(OID.ROOT, "moved", bar2, ObjectType.FOLDER, bar),
                insert(bar2, "baz", baz2, ObjectType.FILE, baz),
                insert(bar2, "qux", qux2, ObjectType.FOLDER, qux),
                updateContent(baz2, did, h, 0L, 42L)
        );

        LogicalObjectsPrinter.printRecursively(rootSID, ds);

        // verify
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                folder("moved", bar2,
                        file("baz", baz2,
                                content(CONTENT, 1234),
                                content(EMPTY, 42)),
                        folder("qux", qux2)),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH,
                                folder(bar.toStringFormal(), bar,
                                        file("baz", baz),
                                        folder("qux", qux)))));

        assertHasContentChanges(sidx, baz2);
    }
}
