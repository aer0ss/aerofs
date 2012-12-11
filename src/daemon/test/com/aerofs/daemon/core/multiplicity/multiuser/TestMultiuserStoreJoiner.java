/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.UserID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class TestMultiuserStoreJoiner extends AbstractTest
{
    @Mock StoreCreator sc;
    @Mock Trans t;

    @InjectMocks MultiuserStoreJoiner msj;

    SIndex sidx = new SIndex(123);

    UserID userID = UserID.fromInternal("test@gmail");

    @Test
    public void joinStore_shouldJoinRootStore()
            throws Exception
    {
        SID rootSID = SID.rootSID(userID);
        msj.joinStore_(sidx, rootSID, "test", t);

        verify(sc).createRootStore_(eq(rootSID), any(Path.class), eq(t));
    }

    @Test
    public void joinStore_shouldNotJoinNonRootStore()
            throws Exception
    {
        SID sid = SID.generate();
        msj.joinStore_(sidx, sid, "test", t);

        verifyNoMoreInteractions(sc);
    }
}
