/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.ds;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserPathResolver;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserStores;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.MetaDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.cfg.CfgStorageType;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDirectoryService_SwapOIDs extends AbstractTest
{
    @Mock SIDMap sm;

    private final InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
    private final IMetaDatabase mdb = new MetaDatabase(dbcw.getCoreDBCW());

    // System under test
    private final DirectoryServiceImpl ds = new DirectoryServiceImpl();

    final SID sid = SID.generate();
    final SIndex sidx = new SIndex(1);
    @Mock Trans t;

    private OID o1 = new OID(UniqueID.generate());
    private OID o1_1 = new OID(UniqueID.generate());
    private OID o1_2 = new OID(UniqueID.generate());
    private OID o3 = new OID(UniqueID.generate());
    private OID o3_1 = new OID(UniqueID.generate());


    @Before
    public void setup() throws Exception
    {
        dbcw.init_();

        SingleuserStores sss = mock(SingleuserStores.class);
        // Make sidx a root
        when(sss.isRoot_(sidx)).thenReturn(true);
        when(sss.getUserRoot_()).thenReturn(sidx);

        when(sm.get_(sid)).thenReturn(sidx);
        when(sm.get_(sidx)).thenReturn(sid);

        final AbstractPathResolver pr = new SingleuserPathResolver(sss, ds, sm, sm);

        ds.inject_(mdb, mock(MapAlias2Target.class), mock(TransManager.class),
                mock(IMapSID2SIndex.class),
                mock(StoreDeletionOperators.class), pr);

        setupSwapTest();
    }

    @After
    public void tearDown() throws Exception
    {
        dbcw.fini_();
    }

    private void setupSwapTest() throws Exception
    {
        //  Create:
        //  root
        //   |_o1
        //      |_ o1_1
        //      |   |_ o3
        //      |       |_o3_1
        //      |
        //      |_ o1_2

        // TODO (MJ) the following root initialization is copy-pasted from StoreCreator
        mdb.insertOA_(sidx, OID.ROOT, OID.ROOT, OA.ROOT_DIR_NAME, Type.DIR, 0, t);

        ds.createOA_(Type.DIR, sidx, o1, OID.ROOT, o1.toString(), 0, t);
        ds.createOA_(Type.DIR, sidx, o1_1, o1, o1_1.toString(), 0, t);
        ds.createOA_(Type.DIR, sidx, o1_2, o1, o1_2.toString(), 0, t);
        ds.createOA_(Type.DIR, sidx, o3, o1_1, o3.toString(), 0, t);
        ds.createOA_(Type.DIR, sidx, o3_1, o3, o3_1.toString(), 0, t);
    }

    @Test
    public void whenSwappingOIDs_shouldHaveChildrenAndParentOfOldObject() throws Exception
    {
        ds.swapOIDsInSameStore_(sidx, o1, o3, t);
        verifySwappedObjectHasChildrenAndParentOfOldObject();
    }

    @Test
    public void whenSwappingOIDsWithSameName_shouldHaveChildrenAndParentOfOldObject()
            throws Exception
    {
        // Change the name of o3 to match the name of o1
        OA oa1_1 = ds.getOA_(new SOID(sidx, o1_1));
        OA oa3 = ds.getOA_(new SOID(sidx, o3));
        assertTrue(oa3.parent().equals(oa1_1.soid().oid()));
        ds.setOAParentAndName_(oa3, oa1_1, ds.getOA_(new SOID(sidx, o1)).name(), t);

        ds.swapOIDsInSameStore_(sidx, o1, o3, t);
        verifySwappedObjectHasChildrenAndParentOfOldObject();
    }

    private void verifySwappedObjectHasChildrenAndParentOfOldObject() throws Exception
    {
        //  Verify:
        //  root
        //   |_o3
        //      |_ o1_1
        //      |   |_ o1
        //      |       |_o3_1
        //      |
        //      |_ o1_2

        SOID soid3 = new SOID(sidx, o3);
        assertEquals(OID.ROOT, ds.getOA_(soid3).parent());
        assertEquals(ImmutableSet.of(o1_1, o1_2), ds.getChildren_(soid3));

        SOID soid1 = new SOID(sidx, o1);
        assertEquals(o1_1, ds.getOA_(soid1).parent());
        assertEquals(ImmutableSet.of(o3_1), ds.getChildren_(soid1));
    }

}
