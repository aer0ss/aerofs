package com.aerofs.daemon.core.lib;

import com.aerofs.base.acl.Permissions;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.mockito.Mock;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BaseStoreJoinerTest extends AbstractTest
{
    protected @Mock IMapSIndex2SID sidx2sid;
    protected @Mock StoreCreator sc;
    protected @Mock Trans t;
    protected IStoreJoiner isj;

    UserID userID = UserID.fromInternal("test@gmail");
    SIndex sidx = new SIndex(123);

    protected static IStoreJoiner.StoreInfo sf(String name, boolean external)
    {
        return new IStoreJoiner.StoreInfo(name, external,
                ImmutableMap.<UserID, Permissions>of(), ImmutableSet.of());
    }

    public void joinStore_shouldJoinRootStore() throws Exception
    {
        SID rootSID = SID.rootSID(userID);
        when(sidx2sid.getNullable_(sidx)).thenReturn(rootSID);

        isj.joinStore_(sidx, rootSID, sf("test", false), t);

        verify(sc).createRootStore_(eq(rootSID), eq("test"), eq(t));
    }

    public void joinStore_shouldJoinNonRootStore() throws Exception
    {
        SID sid = SID.generate();
        when(sidx2sid.getNullable_(sidx)).thenReturn(sid);

        isj.joinStore_(sidx, sid, sf("test", false), t);

        verify(sc).createRootStore_(eq(sid), eq("test"), eq(t));
    }
}
