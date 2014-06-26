/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.ds;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDirectoryService_Expulsion extends AbstractTest
{
    @Mock SIDMap sm;
    @Mock Trans t;

    final SID sid = SID.generate();
    final SIndex sidx = new SIndex(1);

    private final InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
    private final IMetaDatabase mdb = new MetaDatabase(dbcw.getCoreDBCW());

    // System under test
    private final DirectoryServiceImpl ds = new DirectoryServiceImpl();

    private final OID d0 = OID.generate();
    private final OID d1 = OID.generate();
    private final OID d2 = OID.generate();
    private final OID f3 = OID.generate();

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
    }

    @After
    public void tearDown() throws Exception
    {
        dbcw.fini_();
    }

    @Test
    public void shouldComputeInheritedExpulsionFlag() throws Exception
    {
        mdb.insertOA_(sidx, d0, OID.ROOT, "d0", Type.DIR, 0, t);
        mdb.insertOA_(sidx, d1, d0, "d1", Type.DIR, OA.FLAG_EXPELLED_ORG, t);
        mdb.insertOA_(sidx, d2, d1, "f2", Type.DIR, 0, t);
        mdb.insertOA_(sidx, f3, d2, "f3", Type.FILE, 0, t);

        assertFalse(ds.getOA_(new SOID(sidx, d0)).isExpelled());
        assertFalse(ds.getOA_(new SOID(sidx, d0)).isSelfExpelled());
        assertTrue(ds.getOA_(new SOID(sidx, d1)).isExpelled());
        assertTrue(ds.getOA_(new SOID(sidx, d1)).isSelfExpelled());
        assertTrue(ds.getOA_(new SOID(sidx, d2)).isExpelled());
        assertFalse(ds.getOA_(new SOID(sidx, d2)).isSelfExpelled());
        assertTrue(ds.getOA_(new SOID(sidx, f3)).isExpelled());
        assertFalse(ds.getOA_(new SOID(sidx, f3)).isSelfExpelled());
    }
}
