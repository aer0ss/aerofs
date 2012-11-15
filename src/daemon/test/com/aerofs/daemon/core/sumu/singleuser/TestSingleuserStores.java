/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.sumu.singleuser;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.store.IStoreDeletionListener.StoreDeletionNotifier;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class TestSingleuserStores extends AbstractTest
{
    @Mock TransManager tm;
    @Mock StoreCreator sc;
    @Mock SIDMap sm;
    @Mock MapSIndex2Store sidx2s;
    @Mock DevicePresence dp;
    @Mock MapSIndex2DeviceBitMap sidx2dbm;
    @Mock StoreDeletionNotifier sdn;
    @Mock IStoreDatabase sdb;

    @Mock Store store;
    @Mock Trans t;

    @InjectMocks SingleuserStores sss;

    SIndex sidx = new SIndex(1);
    SIndex sidxParent = new SIndex(2);

    @Before
    public void setup() throws SQLException
    {
        sss.inject_(sdb, tm, sc, sm, sidx2s, sidx2dbm, dp, sdn);
        when(sidx2s.get_(sidx)).thenReturn(store);
    }

    /**
     * isRoot(sidx) should return true if and only if getParent(sidx) returns sidx.
     */
    @Test
    public void shouldMaintainConsistencyBetweenIsRootAndGetParent()
            throws SQLException, IOException, ExAlreadyExist
    {
        sss.add_(sidx, sidxParent, t);
        sss.add_(sidxParent, sidxParent, t);

        assertFalse(sss.getParent_(sidx).equals(sidx));
        assertFalse(sss.isRoot_(sidx));

        assertTrue(sss.getParent_(sidxParent).equals(sidxParent));
        assertTrue(sss.isRoot_(sidxParent));
    }

}
