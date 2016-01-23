package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.mock.logical.LogicalObjectsPrinter;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.block.BlockStorage;
import com.aerofs.daemon.core.phy.block.BlockStorageDatabase;
import com.aerofs.daemon.core.phy.block.BlockStorageSchema;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.CfgAbsDefaultAuxRoot;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.testlib.UnitTestTempDir;
import com.google.common.collect.ImmutableSet;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.core.polaris.InMemoryDS.*;
import static com.aerofs.daemon.core.polaris.InMemoryDS.content;
import static com.aerofs.daemon.core.polaris.InMemoryDS.folder;
import static com.aerofs.daemon.core.polaris.api.RemoteChange.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestApplyChange_Migrate extends AbstractTestApplyChange {
    OID foo = OID.generate();
    OID bar = OID.generate();
    OID baz = OID.generate();
    OID qux = OID.generate();

    DID did = DID.generate();
    static final ContentHash h = new ContentHash(BaseSecUtil.hash());

    static final byte[] EMPTY = {};
    static final byte[] CONTENT = {'d', 'e', 'a', 'd'};

    @Rule
    public final UnitTestTempDir tmp = new UnitTestTempDir();

    // TODO: parameterized test to exercise both BlockStorage and LinkedStorage
    @Override
    public void setUp() throws Exception
    {
        ps = new BlockStorage();
        super.setUp();
        try (Statement s = dbcw.getConnection().createStatement()) {
            new BlockStorageSchema().create_(s, dbcw);
        }
        dbcw.commit_();
        CfgAbsDefaultAuxRoot aux = mock(CfgAbsDefaultAuxRoot.class);
        when(aux.get()).thenReturn(tmp.getTestTempDir().getAbsolutePath());
        CfgStoragePolicy policy = mock(CfgStoragePolicy.class);
        when(policy.useHistory()).thenReturn(true);
        ((BlockStorage)ps).inject_(aux, policy,
                mock(TokenManager.class), tm, inj.getInstance(CoreScheduler.class),
                new InjectableFile.Factory(), mock(IBlockStorageBackend.class),
                inj.getInstance(BlockStorageDatabase.class), ImmutableSet.of());
        ps.init_();
    }

    @Override
    protected void mockPhy(SIndex sidx, OID oid, KIndex kidx, byte[] c, long mtime, Trans t)
            throws SQLException, IOException {
        SOKID k = new SOKID(sidx, oid, kidx);
        IPhysicalPrefix prefix = ps.newPrefix_(k, null);
        try (OutputStream os = prefix.newOutputStream_(false)) {
            os.write(c);
        }
        ps.apply_(prefix, ps.newFile_(inj.getInstance(DirectoryService.class).resolve_(k.soid()),
                kidx), false, mtime, t);
    }

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
        // NB: conflict branch is lost when deletion happens first
        mds.expect(rootSID,
                folder(LibParam.TRASH, OID.TRASH,
                        folder(foo.toStringFormal(), foo)),
                folder("moved", bar2,
                        file("baz", baz2,
                                content(CONTENT, 1234)),
                        folder("qux", qux2)),
                anchor("foo", foo,
                        folder(LibParam.TRASH, OID.TRASH,
                                folder(bar.toStringFormal(), bar,
                                        file("baz", baz),
                                        folder("qux", qux)))));

        assertHasContentChanges(sidx, baz2);
    }
}
