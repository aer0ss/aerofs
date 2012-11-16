package com.aerofs.daemon.core.alias;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.alias.Aliasing.AliasAndTarget;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.net.ReceiveAndApplyUpdate;
import com.aerofs.daemon.core.net.ReceiveAndApplyUpdate.CausalityResult;
import com.aerofs.daemon.core.net.proto.PrefixVersionControl;
import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStoreDeletionListener.StoreDeletionNotifier;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.sumu.singleuser.SingleuserPathResolver;
import com.aerofs.daemon.core.sumu.singleuser.SingleuserStores;
import com.aerofs.daemon.lib.db.*;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.db.ver.INativeVersionDatabase;
import com.aerofs.daemon.lib.db.ver.NativeVersionDatabase;
import com.aerofs.daemon.lib.db.ver.TransLocalVersionAssistant;
import com.aerofs.daemon.lib.db.ver.VersionAssistant;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.Path;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;

import static com.aerofs.daemon.core.mock.TestUtilCore.*;


import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.*;
import com.aerofs.proto.Core.PBMeta;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

/**
 *  TestAliasing class is used to test implementation of aliasing algorithm.
 *
 *  In addition to giving appropriate names, tests have been labeled as described
 *  in the state transition diagram in the aliasing documentation
 *  path-to-repo/docs/design/name_conflicts/name_conflict_resolution.doc
 */

// Ignore this test class for now as it is not maintenable at all. In the future we should write
// integration tests for the aliasing subsystem. Talk to Mark or Weihan for detail.
@Ignore
public class TestAliasing extends AbstractTest
{
    private final CfgLocalDID cfgLocalDID = mock(CfgLocalDID.class);
    private final InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
    private final INativeVersionDatabase nvdb = new NativeVersionDatabase(dbcw.mockCoreDBCW(), cfgLocalDID);
    TransLocalVersionAssistant tlva = mock(TransLocalVersionAssistant.class);
    VersionAssistant va = mock(VersionAssistant.class);
    ActivityLog alog = mock(ActivityLog.class);

    private final IMetaDatabase mdb = new MetaDatabase(dbcw.mockCoreDBCW());
    private final ISIDDatabase sdb = new SIDDatabase(dbcw.mockCoreDBCW());
    private final IAliasDatabase aldb = new AliasDatabase(dbcw.mockCoreDBCW());
    private final MapAlias2Target alias2target = new MapAlias2Target(aldb);
    private final ICollectorSequenceDatabase csdb = new CollectorSequenceDatabase(dbcw.mockCoreDBCW());
    private final IMapSIndex2SID sidx2sid = new SIDMap(sdb);
    private final StoreDeletionNotifier sdn = mock(StoreDeletionNotifier.class);
    private final NativeVersionControl nvc = new NativeVersionControl(nvdb, csdb, alias2target,
            cfgLocalDID, tlva, alog, sdn);
    private final PrefixVersionControl pvc = mock(PrefixVersionControl.class);

    private final IPhysicalStorage ps = mock(IPhysicalStorage.class);
    private final IMapSID2SIndex sid2sidx = mock(IMapSID2SIndex.class);

    private DirectoryService ds;

    private final Hasher hasher = mock(Hasher.class);
    private final ObjectMover om = mock(ObjectMover.class);
    private final BranchDeleter bd = mock(BranchDeleter.class);

    private final ObjectCreator oc = mock(ObjectCreator.class);
    private final ReceiveAndApplyUpdate ru = mock(ReceiveAndApplyUpdate.class);
    private final VersionUpdater vu = mock(VersionUpdater.class);
    private final TransManager tm = mock(TransManager.class);
    private final SingleuserStores sss = mock(SingleuserStores.class);

    // System under test.
    private Aliasing al = new Aliasing();

    private final Trans t = mock(Trans.class);
    private static final DID localDID = new DID(UniqueID.generate());
    private static final DID remoteDID = new DID(UniqueID.generate());
    private SIndex sidx;
    private OID oidParent;
    private static final String conflictFileName = "foo";
    private static final Path parentPath = new Path("level1", "level2");
    private static final long len = 1024;
    private static final long mtime = 1329528440;
    private SOID soidTarget, soidAlias;
    private SOID soidLocal, soidRemote;
    private Set<OCID> requested = Sets.newTreeSet();
    SOCID aliasMeta, aliasContent, targetMeta, targetContent;

    private void tearDownInMemoryDatabase() throws Exception
    {
        dbcw.fini_();
    }

    @Before
    public void setUp() throws Exception
    {
        DirectoryService realDS = new DirectoryService();
        SingleuserPathResolver pathResolver = new SingleuserPathResolver(sss, realDS, sidx2sid);
        realDS.inject_(ps, mdb, alias2target, tm, sid2sidx, null,
                mock(FrequentDefectSender.class), sdn, pathResolver);
        ds = spy(realDS);

        AliasingMover almv = new AliasingMover(ds, hasher, om, pvc, nvc, bd);
        MapAlias2Target a2t = new MapAlias2Target(aldb);

        al = new Aliasing();
        al.inject_(ds, nvc, vu, oc, om, ru, almv, a2t, tm);

        when(cfgLocalDID.get()).thenReturn(localDID);
        dbcw.init_();
        nvc.init_();

        sidx = new SIndex(1);
        oidParent = new OID(UniqueID.generate());

        SOID soid1 = new SOID(sidx, new OID(UniqueID.generate()));
        SOID soid2 = new SOID(sidx, new OID(UniqueID.generate()));

        // Choose alias and target among the two SOIDs.
        AliasAndTarget ar = Aliasing.determineAliasAndTarget_(soid1, soid2);
        soidAlias = ar._alias;
        soidTarget = ar._target;

        aliasMeta = new SOCID(soidAlias, CID.META);
        aliasContent = new SOCID(soidAlias, CID.CONTENT);
        targetMeta = new SOCID(soidTarget, CID.META);
        targetContent = new SOCID(soidTarget, CID.CONTENT);

        when(tm.begin_()).thenReturn(t);
        when(tlva.get(any(Trans.class))).thenReturn(va);

    }

    @After
    public void tearDown() throws Exception
    {
        tearDownInMemoryDatabase();
    }

    private void mockObjectCreationAndMovementMethods() throws Exception
    {
        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocationOnMock)
                throws Throwable
            {
                Object[] args = invocationOnMock.getArguments();

                SOID soid = (SOID) args[0];
                OID oidParent = (OID) args[1];
                String newName = (String) args[2];
                Trans t = (Trans) args[6];

                mdb.setOAParentAndName_(soid.sidx(), soid.oid(), oidParent, newName, t);
                return null;
            }
        }).when(om).moveInSameStore_(any(SOID.class), any(OID.class), anyString(),
                any(PhysicalOp.class), anyBoolean(), anyBoolean(), any(Trans.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock)
                throws Throwable
            {
                Object[] args = invocationOnMock.getArguments();

                Type type = (Type) args[0];
                SOID soid = (SOID) args[1];
                OID oidParent = (OID) args[2];
                String createName = (String) args[3];
                int flags = (Integer) args[4];
                Trans t = (Trans) args[8];

                mdb.createOA_(soid.sidx(), soid.oid(), oidParent, createName, type, flags, t);

                return null;
            }
        }).when(oc).createMeta_(any(OA.Type.class), any(SOID.class), any(OID.class),
            anyString(), anyInt(), any(PhysicalOp.class), anyBoolean(), anyBoolean(),
            any(Trans.class));
    }

    private void mockDeleteBranch() throws Exception
    {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock)
                throws Throwable
            {
                Object[] args = invocationOnMock.getArguments();

                SOCKID k = (SOCKID) args[0];
                Version v = (Version) args[1];
                Trans t = (Trans) args[3];

                nvc.deleteLocalVersion_(k, v, t);
                mdb.deleteCA_(k.soid(), k.kidx(), t);
                return null;
            }
        }).when(bd).deleteBranch_(any(SOCKID.class), any(Version.class), anyBoolean(),
            any(Trans.class));
    }

    private void mockAtomicAliasWriteMethod() throws Exception
    {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock)
                throws Throwable
            {
                Object[] args = invocationOnMock.getArguments();

                SOCKID k = (SOCKID) args[0];
                Trans t = (Trans) args[1];

                nvc.updateMyVersion_(k, true, t);
                return null;
            }
        }).when(vu).updateAliased_(any(SOCKID.class), any(Trans.class));
    }

    /**
     * Builds the protobuf meta-data object that will be processed in GetComponentReply (and eventually
     * Aliasing) when non-alias message is received.
     */
    private PBMeta buildNonAliasPBMeta() throws Exception
    {
        return PBMeta.newBuilder()
            .setName(conflictFileName)
            .setParentObjectId(oidParent.toPB())
            .setType(PBMeta.Type.FILE)
            .setFlags(0x0)
            .build();
    }

    /**
     * Builds the protobuf meta-data object that will be processed in GetComponentReply (and eventually
     * Aliasing) when alias message is received.
     *
     * oidTarget and vRemoteTarget are the additional fields in PBMeta that differentiates
     * an alias message from a non-alias message.
     */
    private PBMeta buildAliasPBMeta(OID oidTarget, Version vRemoteTarget) throws Exception
    {
         return PBMeta.newBuilder()
            .setName(conflictFileName)
            .setParentObjectId(oidParent.toPB())
            .setType(PBMeta.Type.FILE)
            .setFlags(0x0)
            .setTargetOid(oidTarget.toPB())
            .setTargetVersion(vRemoteTarget.toPB_())
            .build();
    }

    private void mockHasAliasedOA(SOID soid) throws Exception
    {
        doAnswer(new Answer<Boolean>()
        {
            @Override
            public Boolean answer(InvocationOnMock invocationOnMock)
                throws Throwable
            {
                Object[] args = invocationOnMock.getArguments();
                SOID soid = (SOID) args[0];

                OA oa = mdb.getOA_(soid);
                if (oa != null) return true;

                OID aliasedToTarget = aldb.getTargetOID_(soid.sidx(), soid.oid());
                return aliasedToTarget != null;
            }
        }).when(ds).hasAliasedOA_(soid);
    }

    private void verifyWhetherReceiveAndApplyUpdateObjectIsUsed(boolean used) throws
        Exception
    {
        verify(ru, used ? atLeastOnce() : never()).computeCausalityForMeta_(any(SOID.class),
            any(Version.class), anyInt());
        verify(ru, used ? atLeastOnce() : never()).applyMeta_(any(DID.class), any(SOID.class),
            any(PBMeta.class), any(OID.class), anyBoolean(), anyInt(), any(Trans.class),
            any(SOID.class), any(Version.class), any(SOID.class), anySetOf(OCID.class),
            any(CausalityResult.class));
        verify(ru, never()).applyUpdateMetaAndContent_(any(SOCKID.class), any(Version.class),
            any(CausalityResult.class), any(Trans.class));
    }

    private void verifyReceiveAndApplyUpdateObjectIsNotUsed() throws Exception
    {
        verifyWhetherReceiveAndApplyUpdateObjectIsUsed(false);
    }

    private void verifyReceiveAndApplyUpdateObjectIsUsedAndNameConflictIsDetected()
        throws Exception
    {
        verifyWhetherReceiveAndApplyUpdateObjectIsUsed(true);
    }

    /**
     * This is a mock implementation of ReceiveAndApplyUpdate.applyMeta_() method
     * when an unknown remote non-alias is received via alias message which will
     * result in name conflict on a local non-alias.
     * @throws Exception
     */
    private void mockApplyMetaOnNameConflict() throws Exception
    {
        when(ru.applyMeta_(any(DID.class), any(SOID.class), any(PBMeta.class),
                any(OID.class), anyBoolean(), anyInt(), any(Trans.class), any(SOID.class),
                any(Version.class), any(SOID.class), anySetOf(OCID.class),
                any(CausalityResult.class)))
                .thenAnswer(new Answer<Object>()
                {
                    @Override
                    public Object answer(InvocationOnMock invocationOnMock)
                            throws Throwable
                    {
                        Object[] args = invocationOnMock.getArguments();

                        SOID soidReceived = (SOID) args[1];
                        OID oidParent = (OID) args[3];
                        Trans t = (Trans) args[6];
                        Version vReceivedMeta = (Version) args[9];
                        OID alias, target;

                        AliasAndTarget ar = Aliasing.determineAliasAndTarget_(soidReceived,
                                soidLocal);
                        if (ar._target.equals(soidReceived)) {
                            mdb.deleteOA_(soidLocal.sidx(), soidLocal.oid(), t);
                            mdb.deleteCA_(soidLocal, KIndex.MASTER, t);

                            mdb.createOA_(soidReceived.sidx(), soidReceived.oid(), oidParent,
                                    conflictFileName, Type.FILE, 0x0, t);
                            mdb.createCA_(soidReceived, KIndex.MASTER, t);
                            mdb.setCA_(soidReceived, KIndex.MASTER, len, mtime, null, t);

                            SOCKID kLocalMeta = new SOCKID(soidLocal, CID.META, KIndex.MASTER);
                            SOCKID kLocalContent = new SOCKID(soidLocal, CID.CONTENT,
                                    KIndex.MASTER);

                            Version vLocalMeta = nvdb.getLocalVersion_(kLocalMeta);
                            Version vLocalContent = nvdb.getLocalVersion_(kLocalContent);
                            nvdb.deleteLocalVersion_(kLocalMeta, vLocalMeta, t);
                            nvdb.deleteLocalVersion_(kLocalContent, vLocalContent, t);

                            Version vMergedMeta = vReceivedMeta.add_(vLocalMeta);
                            nvdb.addLocalVersion_(new SOCKID(soidReceived, CID.META, KIndex.MASTER),
                                    vMergedMeta, t);
                            nvdb.addLocalVersion_(
                                    new SOCKID(soidReceived, CID.CONTENT, KIndex.MASTER),
                                    vLocalContent, t);
                            alias = soidLocal.oid();
                            target = soidReceived.oid();
                        } else {
                            nvdb.addLocalVersion_(new SOCKID(soidLocal, CID.META, KIndex.MASTER),
                                    vReceivedMeta.withoutAliasTicks_(), t);
                            nvdb.addLocalVersion_(new SOCKID(soidReceived, CID.META, KIndex.MASTER),
                                    vReceivedMeta.sub_(vReceivedMeta.withoutAliasTicks_()), t);

                            alias = soidReceived.oid();
                            target = soidLocal.oid();
                        }
                        aldb.addAliasToTargetMapping_(sidx, alias, target, t);
                        aldb.resolveAliasChaining_(sidx, alias, target, t);

                        return true;
                    }
                });
    }

    private void mockLocalOA(SOID soid, OA oa, String fileName) throws Exception
    {
        mockOA(oa, soid, Type.FILE, false, oidParent, fileName, ds);
        mockBranches(oa, 1, len, mtime, null);
        mdb.createOA_(soid.sidx(), soid.oid(), oidParent, fileName, Type.FILE, 0x0, t);
        mdb.createCA_(soid, KIndex.MASTER, t);
        mdb.setCA_(soid, KIndex.MASTER, len, mtime, null, t);
    }

    private void verifyAliasMappingAndNoChainingFor3OIDs(SOID soid1, SOID soid2, SOID soid3)
        throws Exception
    {
        assertNull(mdb.getOA_(soid1));
        assertNull(mdb.getOA_(soid2));

        OA targetOA = mdb.getOA_(soid3);
        assertNotNull(targetOA);
        assertEquals(targetOA.name(), conflictFileName);

        // Verify the alias mappings and no chaining.
        assertEquals(soid3.oid(), aldb.getTargetOID_(sidx, soid1.oid()));
        assertEquals(soid3.oid(), aldb.getTargetOID_(sidx, soid2.oid()));
    }

    private void mockHasAliasedOAFor3OIDs(SOID soid1, SOID soid2, SOID soid3) throws Exception
    {
        mockHasAliasedOA(soid1);
        mockHasAliasedOA(soid2);
        mockHasAliasedOA(soid3);
    }

    // TG
    @Test
    public void shouldAliasLocalNonAliasObjectWhenNewRemoteNonAliasObjectIsReceivedWithSameName()
        throws Exception
    {
        soidRemote = soidTarget;
        soidLocal = soidAlias;

        // Start mocking objects.
        DID aliasDID = localDID;
        Version vAliasMeta = new Version().set_(aliasDID, new Tick(10));
        Version vAliasContent = new Version().set_(aliasDID, new Tick(100));
        nvdb.addLocalVersion_(new SOCKID(aliasMeta, KIndex.MASTER), vAliasMeta, t);
        nvdb.addLocalVersion_(new SOCKID(aliasContent, KIndex.MASTER), vAliasContent, t);

        DID targetDID = remoteDID;
        Version vKMLTargetMeta = new Version().set_(targetDID, new Tick(50));
        Version vKMLTargetContent = new Version().set_(targetDID, new Tick(500));
        nvdb.addKMLVersion_(targetMeta, vKMLTargetMeta, t);
        nvdb.addKMLVersion_(targetContent, vKMLTargetContent, t);

        // Version of the remote object received is same as the KML version
        // of the remote object.
        Version vRemote = vKMLTargetMeta;

        when(ds.resolveNullable_(new SOID(sidx, oidParent))).thenReturn(parentPath);
        when(ds.resolveNullable_(parentPath.append(Util.newNextFileName(conflictFileName)))).thenReturn(null);

        mockObjectCreationAndMovementMethods();

        // Mock local object that will be aliased.
        OA oaAlias = mock(OA.class);
        mockLocalOA(soidAlias, oaAlias, conflictFileName);
        mockCA(oaAlias, KIndex.MASTER, 0, 0);

        // Mock the remote object that will be target.
        OA oaTarget = mock(OA.class);
        mockOA(oaTarget, soidTarget, Type.FILE, false, oidParent, conflictFileName, ds);
        mockCA(oaTarget, KIndex.MASTER, 0, 0);

        when(ds.getOANullable_(soidLocal)).thenReturn(oaAlias);
        when(ds.hasOA_(soidAlias)).thenReturn(true);
        when(ds.hasOA_(soidTarget)).thenReturn(true);

        mockDeleteBranch();
        PBMeta meta = buildNonAliasPBMeta();
        mockAtomicAliasWriteMethod();
        // Done mocking objects.

        // Run the aliasing alogrithm for new remote object.
        al.resolveNameConflictOnNewRemoteObjectByAliasing_(soidRemote, soidLocal, oidParent,
                vRemote, meta, null, t);

        // Database verification phase.
        assertEquals(soidTarget.oid(), aldb.getTargetOID_(soidAlias.sidx(), soidAlias.oid()));

        Version vMergedMeta = vRemote.add_(vAliasMeta);
        assertEquals(vMergedMeta, nvdb.getLocalVersion_(new SOCKID(targetMeta, KIndex.MASTER)));
        assertEquals(vAliasContent, nvdb.getLocalVersion_(new SOCKID(targetContent, KIndex.MASTER)));

        Version vAfterAliasMeta = nvdb.getLocalVersion_(new SOCKID(aliasMeta, KIndex.MASTER));
        assertTrue(vAfterAliasMeta.get_(localDID).isAlias());

        assertNull(mdb.getOA_(soidAlias));

        OA targetOA = mdb.getOA_(soidTarget);
        assertNotNull(targetOA);
        assertEquals(targetOA.name(), conflictFileName);

        // Ensure required methods were invoked.
        verify(bd).deleteBranch_(new SOCKID(aliasContent, KIndex.MASTER), vAliasContent, false,
                true, t);
        verify(vu).updateAliased_(new SOCKID(aliasMeta, KIndex.MASTER), t);
        verify(hasher, never()).computeHashBlocking_(any(SOKID.class));

        verify(ds).deleteOA_(soidAlias, t);
    }

    // TB
    @Test
    public void shouldAliasRemoteNonAliasObjectWhenNewRemoteNonAliasObjectIsReceivedWithSameName()
        throws Exception
    {
        soidRemote = soidAlias;
        soidLocal = soidTarget;

        // Start mocking objects.
        DID targetDID = localDID;
        Version vTargetMeta = new Version().set_(targetDID, new Tick(10));
        Version vTargetContent = new Version().set_(targetDID, new Tick(100));
        nvdb.addLocalVersion_(new SOCKID(targetMeta, KIndex.MASTER), vTargetMeta, t);
        nvdb.addLocalVersion_(new SOCKID(targetContent, KIndex.MASTER), vTargetContent, t);

        DID aliasDID = remoteDID;
        Version vKMLAliasMeta = new Version().set_(aliasDID, new Tick(50));
        Version vKMLAliasContent = new Version().set_(aliasDID, new Tick(500));
        nvdb.addKMLVersion_(aliasMeta, vKMLAliasMeta, t);
        nvdb.addKMLVersion_(aliasContent, vKMLAliasContent, t);

        Version vRemote = vKMLAliasMeta;

        doReturn(parentPath).when(ds).resolveNullable_(new SOID(sidx, oidParent));
        doReturn(null).when(ds).resolveNullable_(parentPath.append(Util.newNextFileName(conflictFileName)));

        mockObjectCreationAndMovementMethods();

        // Mock local object that will be target.
        OA oaTarget = mock(OA.class);
        mockLocalOA(soidTarget, oaTarget, conflictFileName);

        // Mock remote object that will be alias.
        OA oaAlias = mock(OA.class);
        mockOA(oaAlias, soidAlias, Type.FILE, false, oidParent, conflictFileName, ds);
        mockCA(oaAlias, KIndex.MASTER, 0, 0);

        doReturn(oaTarget).when(ds).getOANullable_(soidLocal);
        doReturn(true).when(ds).hasOA_(soidAlias);
        doReturn(true).when(ds).hasOA_(soidTarget);

        PBMeta meta = buildNonAliasPBMeta();
        mockAtomicAliasWriteMethod();
        // Done mocking objects.

        // Run the aliasing alogrithm for new remote object.
        al.resolveNameConflictOnNewRemoteObjectByAliasing_(soidRemote, soidLocal, oidParent,
                vRemote, meta, null, t);

        // Database verification phase.
        verify(om, never()).moveInSameStore_(any(SOID.class), any(OID.class), anyString(),
                any(PhysicalOp.class), anyBoolean(), anyBoolean(), any(Trans.class));

        assertEquals(soidTarget.oid(), aldb.getTargetOID_(soidAlias.sidx(), soidAlias.oid()));

        Version vMergedMeta = vRemote.add_(vTargetMeta);
        assertEquals(vMergedMeta, nvdb.getLocalVersion_(new SOCKID(targetMeta, KIndex.MASTER)));

        Version vAfterAliasMeta = nvdb.getLocalVersion_(new SOCKID(aliasMeta, KIndex.MASTER));
        assertTrue(vAfterAliasMeta.get_(localDID).isAlias());

        assertNull(mdb.getOA_(soidAlias));

        OA targetOA = mdb.getOA_(soidTarget);
        assertNotNull(targetOA);
        assertEquals(targetOA.name(), conflictFileName);

        verify(ds, never()).createCA_(any(SOID.class), any(KIndex.class), any(Trans.class));
        verify(bd, never()).deleteBranch_(any(SOCKID.class), any(Version.class), anyBoolean(),
                any(Trans.class));
        verify(hasher, never()).computeHashBlocking_(any(SOKID.class));
        verify(ds, never()).deleteCA_(any(SOID.class), any(KIndex.class), any(Trans.class));

        verify(vu).updateAliased_(new SOCKID(aliasMeta, KIndex.MASTER), t);

        verify(ds).deleteOA_(soidAlias, t);
    }

    // TI
    @Test
    public void shouldAliasRemoteUnknownAliasObjectToLocalNonAliasObjectWhenAliasMsgIsReceived()
        throws Exception
    {
        // Start mocking objects.
        Version vTargetMeta = new Version().set_(localDID, new Tick(10));
        Version vTargetContent = new Version().set_(localDID, new Tick(100));
        nvdb.addLocalVersion_(new SOCKID(targetMeta, KIndex.MASTER), vTargetMeta, t);
        nvdb.addLocalVersion_(new SOCKID(targetContent, KIndex.MASTER), vTargetContent, t);

        Version vKMLAliasMeta = new Version().set_(remoteDID, new Tick(50));
        Version vKMLAliasContent = new Version().set_(remoteDID, new Tick(500));

        // New version assigned by remote peer to the alias object after aliasing.
        Version vKMLAliasMetaAfterAliasing = new Version().set_(remoteDID, new Tick(11));

        nvdb.addKMLVersion_(aliasMeta, vKMLAliasMeta.add_(vKMLAliasMetaAfterAliasing), t);
        nvdb.addKMLVersion_(aliasContent, vKMLAliasContent, t);

        Version vRemoteTarget = vTargetMeta.add_(vKMLAliasMeta);
        PBMeta meta = buildAliasPBMeta(soidTarget.oid(), vRemoteTarget);
        Version vRemoteAlias = vKMLAliasMetaAfterAliasing;

        mdb.createOA_(soidTarget.sidx(), soidTarget.oid(), oidParent, conflictFileName, Type.FILE,
                0x0, t);

        mdb.createCA_(soidTarget, KIndex.MASTER, t);
        mdb.setCA_(soidTarget, KIndex.MASTER, len, mtime, null, t);

        doReturn(true).when(ds).hasOA_(soidTarget);
        doReturn(true).when(ds).hasAliasedOA_(soidTarget);
        doReturn(mdb.getOA_(soidTarget)).when(ds).getOANullable_(soidTarget);

        doReturn(false).when(ds).hasAliasedOA_(soidAlias);

        // Done mocking objects.

        // Run the test method.
        al.processAliasMsg_(remoteDID, soidAlias, vRemoteAlias, soidTarget, vRemoteTarget,
                oidParent, 0x0, meta, requested);

        // Verification phase.
        assertEquals(soidTarget.oid(), aldb.getTargetOID_(soidAlias.sidx(), soidAlias.oid()));
        assertEquals(vRemoteAlias, nvdb.getLocalVersion_(new SOCKID(aliasMeta, KIndex.MASTER)));

        Version vAfterAliasMeta = nvdb.getLocalVersion_(new SOCKID(aliasMeta, KIndex.MASTER));
        for (Entry<DID, Tick> dt: vAfterAliasMeta.getAll_().entrySet()) {
            assertTrue(dt.getValue().isAlias());
        }

        assertNull(mdb.getOA_(soidAlias));

        OA targetOA = mdb.getOA_(soidTarget);
        assertNotNull(targetOA);
        assertEquals(targetOA.name(), conflictFileName);

        verify(vu, never()).updateAliased_(any(SOCKID.class), any(Trans.class));

        verifyReceiveAndApplyUpdateObjectIsNotUsed();
    }

    // TF
    @Test
    public void shouldDropMsgWhenNonAliasMsgIsReceivedForLocalAliasedObject() throws Exception
    {
        // Mock objects

        mdb.createOA_(soidTarget.sidx(), soidTarget.oid(), oidParent, conflictFileName, Type.FILE,
                0x0, t);

        mdb.createCA_(soidTarget, KIndex.MASTER, t);
        mdb.setCA_(soidTarget, KIndex.MASTER, len, mtime, null, t);

        aldb.addAliasToTargetMapping_(sidx, soidAlias.oid(), soidTarget.oid(), t);

        Version vAliasMeta = new Version().set_(localDID, new Tick(11));
        nvdb.addLocalVersion_(new SOCKID(aliasMeta, KIndex.MASTER), vAliasMeta, t);
        Version vKMLAliasMeta = new Version().set_(remoteDID, new Tick(50));
        nvdb.addKMLVersion_(aliasMeta, vKMLAliasMeta, t);

        Version vTargetMeta = new Version().set_(remoteDID, new Tick(100)).set_(localDID,
            new Tick(500));
        nvdb.addLocalVersion_(new SOCKID(targetMeta, KIndex.MASTER), vTargetMeta, t);

        // Run the test

        al.processNonAliasMsgOnLocallyAliasedObject_(aliasMeta, targetMeta.oid());

        // Verification

        assertEquals(soidTarget.oid(), aldb.getTargetOID_(soidAlias.sidx(), soidAlias.oid()));
        verify(vu, never()).updateAliased_(any(SOCKID.class), any(Trans.class));
        Version vKMLAliasMetaAfter = nvdb.getKMLVersion_(aliasMeta);
        for (Entry<DID, Tick> dt: vKMLAliasMetaAfter.getAll_().entrySet()) {
            assertTrue(dt.getValue().isAlias());
        }

        Version vAllTargetMetaAfter = nvdb.getAllVersions_(targetMeta);

        for (Entry<DID, Tick> dt: vAllTargetMetaAfter.getAll_().entrySet()) {
            assertFalse(dt.getValue().isAlias());
        }

        Version from = vKMLAliasMeta.withoutAliasTicks_();

        /**
         * Target versions must cover alias KML. This can be derived from the algorithm pseudo code:
         *
         * In aliasObjects(o_a, o_t):
         *
         * vl(o_t) = vl(o_t) U vl(o_a);
         * vkml(o_t) = (vkml(o_a) U vkml(o_t)) - vl(o_t)
         *
         * Therefore,
         *
         * vkml(o_t) U vl(o_t) > vkml(o_a)
         */
        assertTrue(from.sub_(vAllTargetMetaAfter).isZero_());

        verifyReceiveAndApplyUpdateObjectIsNotUsed();
    }

    // TJ
    @Test
    public void shouldAliasRemoteUnknownAliasObjectToLocalNonAliasObjectAndResolveChainingWhenAliasMsgIsReceived()
        throws Exception
    {
        UniqueID [] ids = {UniqueID.generate(), UniqueID.generate(), UniqueID.generate()};

        // Order the ids such that id1 < id2 < id3.
        Arrays.sort(ids);

        assert ids.length == 3;
        SOID soid1 = new SOID(sidx, new OID(ids[0]));
        SOID soid2 = new SOID(sidx, new OID(ids[1]));
        SOID soid3 = new SOID(sidx, new OID(ids[2]));

        // Start mocking objects.

        doReturn(true).when(ds).hasAliasedOA_(soid2);
        doReturn(false).when(ds).hasAliasedOA_(soid1);

        doReturn(true).when(ds).hasOA_(soid3);
        doReturn(true).when(ds).hasAliasedOA_(soid3);
        mockOAIsFile(soid3);

        // alias soid2 -> soid3
        aldb.addAliasToTargetMapping_(sidx, soid2.oid(), soid3.oid(), t);

        Version v3 = new Version().set_(localDID, 100);
        nvdb.addLocalVersion_(new SOCKID(soid3, CID.META, KIndex.MASTER), v3, t);

        Version v2 = new Version().set_(localDID, 11);
        nvdb.addLocalVersion_(new SOCKID(soid2, CID.META, KIndex.MASTER), v2, t);

        Version v1 = new Version().set_(remoteDID, 13);
        nvdb.addKMLVersion_(new SOCID(soid1, CID.META), v1, t);

        PBMeta meta = buildAliasPBMeta(soid2.oid(), v2);
        // Done mocking objects.

        al.processAliasMsg_(remoteDID, soid1, v1, soid2, v2, null, 0x0, meta, requested);

        // Verify the alias mappings and no chaining.
        assertEquals(soid3.oid(), aldb.getTargetOID_(sidx, soid1.oid()));
        assertEquals(soid3.oid(), aldb.getTargetOID_(sidx, soid2.oid()));

        assertEquals(v1, nvdb.getLocalVersion_(new SOCKID(soid1, CID.META, KIndex.MASTER)));

        verifyReceiveAndApplyUpdateObjectIsNotUsed();
    }

    // TH
    @Test
    public void shouldMaintainStateWhenKnownAliasToKnownTargetAliasMsgIsReceived() throws Exception
    {
        // Start mocking objects.
        doReturn(true).when(ds).hasOA_(soidTarget);
        doReturn(true).when(ds).hasAliasedOA_(soidTarget);
        doReturn(true).when(ds).hasAliasedOA_(soidAlias);

        aldb.addAliasToTargetMapping_(sidx, soidAlias.oid(), soidTarget.oid(), t);

        Version vTarget = new Version().set_(localDID, 100);
        nvdb.addLocalVersion_(new SOCKID(targetMeta, KIndex.MASTER), vTarget, t);

        Version vAlias = new Version().set_(remoteDID, 13);
        nvdb.addLocalVersion_(new SOCKID(aliasMeta, KIndex.MASTER), vAlias, t);

        PBMeta meta = buildAliasPBMeta(soidTarget.oid(), vTarget);
        // Done mocking objects.

        al.processAliasMsg_(remoteDID, soidAlias, vAlias, soidTarget, vTarget, oidParent,
            0x0, meta, requested);

        // Verification phase.
        assertEquals(soidTarget.oid(), aldb.getTargetOID_(sidx, soidAlias.oid()));

        // Verify versions are maintained.
        assertEquals(vAlias, nvdb.getLocalVersion_(new SOCKID(aliasMeta, KIndex.MASTER)));
        assertEquals(vTarget, nvdb.getLocalVersion_(new SOCKID(targetMeta, KIndex.MASTER)));

        verifyReceiveAndApplyUpdateObjectIsNotUsed();
    }

    // TA
    @Test
    public void shouldAliasLocalNonAliasObjectToRemoteUknownNonAliasWhenAliasMsgIsReceived()
        throws Exception
    {
        // Mock local object that will be aliased.
        OA oaAlias = mock(OA.class);

        mockLocalOA(soidAlias, oaAlias, conflictFileName);
        soidLocal = soidAlias;

        DID aliasDID = localDID;
        Version vAliasMeta = new Version().set_(aliasDID, new Tick(10));
        Version vAliasContent = new Version().set_(aliasDID, new Tick(12));
        nvdb.addLocalVersion_(new SOCKID(aliasMeta, KIndex.MASTER), vAliasMeta, t);
        nvdb.addLocalVersion_(new SOCKID(aliasContent, KIndex.MASTER), vAliasContent, t);

        // Mock the remote object that will be target.
        OA oaTarget = mock(OA.class);
        mockOA(oaTarget, soidTarget, Type.FILE, false, oidParent, conflictFileName, ds);
        Version vTargetMeta = new Version().set_(remoteDID, 100);
        nvdb.addKMLVersion_(targetMeta, vTargetMeta, t);
        Version vTargetMergedMeta = vTargetMeta.add_(vAliasMeta);
        soidRemote = soidTarget;

        mockHasAliasedOA(soidTarget);
        mockHasAliasedOA(soidAlias);

        mockApplyMetaOnNameConflict();
        PBMeta meta = buildAliasPBMeta(soidAlias.oid(), vTargetMergedMeta);
        Version vRemoteAlias = new Version().set_(remoteDID, 113);
        // Done mocking objects.

        al.processAliasMsg_(remoteDID, soidAlias, vRemoteAlias, soidTarget, vTargetMergedMeta,
            oidParent, 0x0, meta, requested);

        // Verification phase
        verifyReceiveAndApplyUpdateObjectIsUsedAndNameConflictIsDetected();

        assertNull(mdb.getOA_(soidAlias));

        OA targetOA = mdb.getOA_(soidTarget);
        assertNotNull(targetOA);
        assertEquals(targetOA.name(), conflictFileName);

        assertEquals(soidTarget.oid(), aldb.getTargetOID_(sidx, soidAlias.oid()));
        assertEquals(vTargetMergedMeta, nvdb.getLocalVersion_(new SOCKID(targetMeta,
            KIndex.MASTER)));
        assertEquals(vRemoteAlias, nvdb.getLocalVersion_(new SOCKID(aliasMeta, KIndex.MASTER)));
    }

    // TC
    @Test
    public void shouldAliasLocalNonAliasObjectToRemoteUnknownNonAliasAndResolveChainingWhenAliasMsgIsReceived()
        throws Exception
    {
        UniqueID [] ids = {UniqueID.generate(), UniqueID.generate(), UniqueID.generate()};

        // Order the ids such that id1 < id2 < id3.
        Arrays.sort(ids);
        assert ids.length == 3;
        SOID soid1 = new SOID(sidx, new OID(ids[0]));
        SOCID socid1Meta = new SOCID(soid1, CID.META);
        SOCID socid1Content = new SOCID(soid1, CID.CONTENT);
        soidLocal = soid1;

        OA oa1 = mock(OA.class);
        mockLocalOA(soid1, oa1, conflictFileName);

        DID did1 = localDID;
        Version v1Meta = new Version().set_(did1, new Tick(10));
        Version v1Content = new Version().set_(did1, new Tick(12));
        nvdb.addLocalVersion_(new SOCKID(socid1Meta, KIndex.MASTER), v1Meta, t);
        nvdb.addLocalVersion_(new SOCKID(socid1Content, KIndex.MASTER), v1Content, t);

        SOID soid2 = new SOID(sidx, new OID(ids[1]));
        DID remote2 = new DID(UniqueID.generate());
        Version v2 = new Version().set_(remote2, 13);

        SOID soid3 = new SOID(sidx, new OID(ids[2]));
        Version v3 = new Version().set_(remoteDID, 100);

        mockHasAliasedOAFor3OIDs(soid1, soid2, soid3);
        when(ds.hasOA_(soid3)).thenReturn(true);
        mockOAIsFile(soid3);

        mockApplyMetaOnNameConflict();
        PBMeta meta = buildAliasPBMeta(soid3.oid(), v3);
        // Done mocking objects.

        al.processAliasMsg_(remoteDID, soid2, v2, soid3, v3, oidParent, 0x0, meta, requested);

        // Verification phase
        verifyReceiveAndApplyUpdateObjectIsUsedAndNameConflictIsDetected();
        verifyAliasMappingAndNoChainingFor3OIDs(soid1, soid2, soid3);

        assertEquals(v1Meta.add_(v3), nvdb.getLocalVersion_(new SOCKID(soid3,
            CID.META, KIndex.MASTER)));
        assertEquals(v2, nvdb.getLocalVersion_(new SOCKID(soid2, CID.META,
            KIndex.MASTER)));
    }

    private void mockOAIsFile(SOID soid)
            throws SQLException, ExNotFound
    {
        OA oa = mock(OA.class);
        when(oa.isFile()).thenReturn(false);
        when(ds.getOA_(soid)).thenReturn(oa);
        when(ds.getOAThrows_(soid)).thenReturn(oa);
        when(ds.getOANullable_(soid)).thenReturn(oa);
    }

    // TE
    @Test
    public void shouldAliasRemoteUnknownNonAliasToLocalNonAliasAndResolveChainingWhenAliasMsgIsReceived()
        throws Exception
    {
        UniqueID [] ids = {UniqueID.generate(), UniqueID.generate(), UniqueID.generate()};

        // Order the ids such that id1 < id2 < id3.
        Arrays.sort(ids);
        assert ids.length == 3;

        SOID soid1 = new SOID(sidx, new OID(ids[0]));
        DID remote1 = new DID(UniqueID.generate());
        Version v1 = new Version().set_(remote1, 13);

        SOID soid2 = new SOID(sidx, new OID(ids[1]));
        Version v2 = new Version().set_(remoteDID, 100);

        SOID soid3 = new SOID(sidx, new OID(ids[2]));
        soidLocal = soid3;

        OA oa3 = mock(OA.class);
        mockLocalOA(soid3, oa3, conflictFileName);

        DID did3 = localDID;
        Version v3Meta = new Version().set_(did3, new Tick(10));
        Version v3Content = new Version().set_(did3, new Tick(12));
        nvdb.addLocalVersion_(new SOCKID(soid3, CID.META, KIndex.MASTER), v3Meta, t);
        nvdb.addLocalVersion_(new SOCKID(soid3, CID.CONTENT, KIndex.MASTER), v3Content, t);

        mockHasAliasedOAFor3OIDs(soid1, soid2, soid3);

        mockApplyMetaOnNameConflict();
        PBMeta meta = buildAliasPBMeta(soid2.oid(), v2);
        // Done mocking objects.

        al.processAliasMsg_(remoteDID, soid1, v1, soid2, v2, oidParent, 0x0, meta, requested);

        // Verification phase
        verifyReceiveAndApplyUpdateObjectIsUsedAndNameConflictIsDetected();
        verifyAliasMappingAndNoChainingFor3OIDs(soid1, soid2, soid3);

        assertEquals(v3Meta.add_(v2), nvdb.getLocalVersion_(new SOCKID(soid3,
            CID.META, KIndex.MASTER)));
        assertEquals(v1, nvdb.getLocalVersion_(new SOCKID(soid1, CID.META,
            KIndex.MASTER)));
    }

    // TN
    @Test
    public void shouldMaintainStateWhenAliasMsgIsReceivedThatMapsLocalAliasToAnotherLocalAlias()
        throws Exception
    {
        UniqueID [] ids = {UniqueID.generate(), UniqueID.generate(), UniqueID.generate()};

        // Order the ids such that id1 < id2 < id3.
        Arrays.sort(ids);
        assert ids.length == 3;

        SOID soid1 = new SOID(sidx, new OID(ids[0]));
        DID remote1 = new DID(UniqueID.generate());
        Version v1 = new Version().set_(remote1, 13);
        nvdb.addLocalVersion_(new SOCKID(soid1, CID.META, KIndex.MASTER), v1, t);

        SOID soid2 = new SOID(sidx, new OID(ids[1]));
        Version v2 = new Version().set_(remoteDID, 21);
        nvdb.addLocalVersion_(new SOCKID(soid2, CID.META, KIndex.MASTER), v2, t);

        SOID soid3 = new SOID(sidx, new OID(ids[2]));
        soidLocal = soid3;

        OA oa3 = mock(OA.class);
        mockLocalOA(soid3, oa3, conflictFileName);

        DID did3 = localDID;
        Version v3Meta = new Version().set_(did3, new Tick(10));
        Version v3Content = new Version().set_(did3, new Tick(12));
        nvdb.addLocalVersion_(new SOCKID(soid3, CID.META, KIndex.MASTER), v3Meta, t);
        nvdb.addLocalVersion_(new SOCKID(soid3, CID.CONTENT, KIndex.MASTER), v3Content, t);

        mockHasAliasedOAFor3OIDs(soid1, soid2, soid3);

        PBMeta meta = buildAliasPBMeta(soid2.oid(), v2);

        aldb.addAliasToTargetMapping_(sidx, soid1.oid(), soid3.oid(), t);
        aldb.addAliasToTargetMapping_(sidx, soid2.oid(), soid3.oid(), t);

        al.processAliasMsg_(remoteDID, soid1, v1, soid2, v2, oidParent, 0x0, meta, requested);

        // Verification phase
        verifyReceiveAndApplyUpdateObjectIsNotUsed();
        verifyAliasMappingAndNoChainingFor3OIDs(soid1, soid2, soid3);

        assertEquals(v1, nvdb.getLocalVersion_(new SOCKID(soid1, CID.META, KIndex.MASTER)));
        assertEquals(v2, nvdb.getLocalVersion_(new SOCKID(soid2, CID.META, KIndex.MASTER)));
    }

    // TM
    @Test
    public void shouldAliasUnknownRemoteNonAliasToLocalNonAliasWhenAliasMsgIsReceivedAndResolveChaining()
        throws Exception
    {
        UniqueID [] ids = {UniqueID.generate(), UniqueID.generate(), UniqueID.generate()};

        // Order the ids such that id1 < id2 < id3.
        Arrays.sort(ids);
        assert ids.length == 3;

        SOID soid1 = new SOID(sidx, new OID(ids[0]));
        DID remote1 = new DID(UniqueID.generate());
        Version v1 = new Version().set_(remote1, 13);
        nvdb.addLocalVersion_(new SOCKID(soid1, CID.META, KIndex.MASTER), v1, t);

        SOID soid2 = new SOID(sidx, new OID(ids[1]));
        Version v2 = new Version().set_(remoteDID, 21);

        SOID soid3 = new SOID(sidx, new OID(ids[2]));
        soidLocal = soid3;

        OA oa3 = mock(OA.class);
        mockLocalOA(soid3, oa3, conflictFileName);

        DID did3 = localDID;
        Version v3Meta = new Version().set_(did3, new Tick(10));
        Version v3Content = new Version().set_(did3, new Tick(12));
        nvdb.addLocalVersion_(new SOCKID(soid3, CID.META, KIndex.MASTER), v3Meta, t);
        nvdb.addLocalVersion_(new SOCKID(soid3, CID.CONTENT, KIndex.MASTER), v3Content, t);

        mockHasAliasedOAFor3OIDs(soid1, soid2, soid3);

        mockApplyMetaOnNameConflict();
        PBMeta meta = buildAliasPBMeta(soid2.oid(), v2);
        aldb.addAliasToTargetMapping_(sidx, soid1.oid(), soid3.oid(), t);

        al.processAliasMsg_(remoteDID, soid1, v1, soid2, v2, oidParent, 0x0, meta, requested);

        // Verification phase
        verifyReceiveAndApplyUpdateObjectIsUsedAndNameConflictIsDetected();
        verifyAliasMappingAndNoChainingFor3OIDs(soid1, soid2, soid3);

        assertEquals(v1, nvdb.getLocalVersion_(new SOCKID(soid1, CID.META, KIndex.MASTER)));
        assertEquals(v2, nvdb.getLocalVersion_(new SOCKID(soid2, CID.META, KIndex.MASTER)));
    }

    // TK
    @Test
    public void shouldAliasLocalNonAliasToRemoteUknownNonAliasAndResolveChainingWhenAliasMsgIsReceived()
        throws Exception
    {
        UniqueID [] ids = {UniqueID.generate(), UniqueID.generate(), UniqueID.generate()};

        // Order the ids such that id1 < id2 < id3.
        Arrays.sort(ids);
        assert ids.length == 3;

        SOID soid1 = new SOID(sidx, new OID(ids[0]));
        DID remote1 = new DID(UniqueID.generate());
        Version v1 = new Version().set_(remote1, 13);
        nvdb.addLocalVersion_(new SOCKID(soid1, CID.META, KIndex.MASTER), v1, t);

        SOID soid2 = new SOID(sidx, new OID(ids[1]));
        soidLocal = soid2;
        OA oa2 = mock(OA.class);
        mockLocalOA(soid2, oa2, conflictFileName);

        DID did2 = localDID;
        Version v2Meta = new Version().set_(did2, new Tick(10));
        Version v2Content = new Version().set_(did2, new Tick(12));
        nvdb.addLocalVersion_(new SOCKID(soid2, CID.META, KIndex.MASTER), v2Meta, t);
        nvdb.addLocalVersion_(new SOCKID(soid2, CID.CONTENT, KIndex.MASTER), v2Content, t);

        SOID soid3 = new SOID(sidx, new OID(ids[2]));
        Version v3 = new Version().set_(remoteDID, 200);

        mockHasAliasedOAFor3OIDs(soid1, soid2, soid3);
        when(ds.hasOA_(soid3)).thenReturn(true);
        mockOAIsFile(soid3);

        mockApplyMetaOnNameConflict();

        PBMeta meta = buildAliasPBMeta(soid3.oid(), v3);

        aldb.addAliasToTargetMapping_(sidx, soid1.oid(), soid2.oid(), t);
        al.processAliasMsg_(remoteDID, soid1, v1, soid3, v3, oidParent, 0x0, meta, requested);

        verifyReceiveAndApplyUpdateObjectIsUsedAndNameConflictIsDetected();
        verifyAliasMappingAndNoChainingFor3OIDs(soid1, soid2, soid3);

        assertEquals(v1, nvdb.getLocalVersion_(new SOCKID(soid1, CID.META, KIndex.MASTER)));
        assertEquals(v2Meta.add_(v3), nvdb.getLocalVersion_(new SOCKID(soid3, CID.META,
            KIndex.MASTER)));
    }


    // Renaming test.
    // In this case 2 objects with different names are present locally and alias message is received
    // that requests one of the objects to be aliased to other.
    // This can happen when one of the objects is renamed during aliasing. Check the aliasing
    // documentation for description of this case.

    private void mockLocalOAWithVersion(SOID soid, String fileName, Version vMeta, Version vContent)
        throws Exception
    {
        OA oa = mock(OA.class);
        mockLocalOA(soid, oa, fileName);
        nvdb.addLocalVersion_(new SOCKID(soid, CID.META, KIndex.MASTER), vMeta, t);
        nvdb.addLocalVersion_(new SOCKID(soid, CID.CONTENT, KIndex.MASTER), vContent, t);
        mockHasAliasedOA(soid);
    }

    @Test
    public void shouldAliasLocalNonAliasToOtherLocalNonAliasWithDifferentNameWhenAliasMsgIsReceived()
        throws Exception
    {
        // Mock local objects with different names.
        Version vAliasMeta = new Version().set_(localDID, new Tick(10));
        Version vAliasContent = new Version().set_(localDID, new Tick(12));
        mockLocalOAWithVersion(soidAlias, conflictFileName + "-alias", vAliasMeta, vAliasContent);

        Version vTargetMeta = new Version().set_(remoteDID, new Tick(100));
        Version vTargetContent = new Version().set_(remoteDID, new Tick(102));
        mockLocalOAWithVersion(soidTarget, conflictFileName, vTargetMeta, vTargetContent);

        PBMeta meta = buildAliasPBMeta(soidTarget.oid(), vTargetMeta);
        mockDeleteBranch();

        byte [] hash = new byte[ContentHash.UNIT_LENGTH];
        for (int i = 0; i < ContentHash.UNIT_LENGTH; i++) hash[i] = (byte) i;

        when(hasher.computeHashBlocking_(any(SOKID.class))).thenReturn(new ContentHash(hash));

        DID did3 = new DID(UniqueID.generate());
        Version vAliasRemoteMeta = new Version().set_(did3, 115);
        Version vTargetRemoteMeta = vAliasMeta.add_(vTargetMeta);
        // Done mocking objects.

        // Invoke alias message handler
        al.processAliasMsg_(did3, soidAlias, vAliasRemoteMeta, soidTarget, vTargetRemoteMeta,
            oidParent, 0x0, meta, requested);

        // Verification phase.
        assertNull(mdb.getOA_(soidAlias));

        OA targetOA = mdb.getOA_(soidTarget);
        assertNotNull(targetOA);
        assertEquals(targetOA.name(), conflictFileName);

        assertEquals(soidTarget.oid(), aldb.getTargetOID_(sidx, soidAlias.oid()));

        assertEquals(vAliasRemoteMeta, nvdb.getLocalVersion_(new SOCKID(soidAlias, CID.META,
            KIndex.MASTER)));
        assertEquals(vTargetRemoteMeta, nvdb.getLocalVersion_(new SOCKID(soidTarget, CID.META,
            KIndex.MASTER)));

        verifyReceiveAndApplyUpdateObjectIsNotUsed();
        verify(hasher).computeHashBlocking_(new SOKID(soidAlias, KIndex.MASTER));
        verify(hasher).computeHashBlocking_(new SOKID(soidTarget, KIndex.MASTER));

        verify(bd).deleteBranch_(new SOCKID(aliasContent, KIndex.MASTER), vAliasContent, true, true,
                t);
        verify(vu, never()).updateAliased_(new SOCKID(aliasMeta, KIndex.MASTER), t);
    }
}
