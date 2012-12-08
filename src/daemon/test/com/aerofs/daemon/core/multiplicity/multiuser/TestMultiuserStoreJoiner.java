/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.UserID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.Maps;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class TestMultiuserStoreJoiner extends AbstractTest
{
    @Mock StoreCreator sc;
    @Mock Trans t;

    @InjectMocks MultiuserStoreJoiner msj;

    SIndex sidx = new SIndex(123);
    UserID tsUserID1 = UserID.fromInternal(":1234");
    UserID tsUserID2 = UserID.fromInternal(":5678");
    {
        assert tsUserID1.isTeamServerID();
        assert tsUserID2.isTeamServerID();
    }

    UserID userID = UserID.fromInternal("test@gmail");
    SID rootSID = SID.rootSID(userID);

    @Test
    public void joinStore_shouldJoinRootStore()
            throws Exception
    {
        Map<UserID, Role> srps = newSRPWithTeamServers();
        srps.put(userID, Role.OWNER);

        msj.joinStore_(sidx, rootSID, "test", srps, t);

        verify(sc).createRootStore_(eq(rootSID), any(Path.class), eq(t));
    }

    // MultiuserStoreJoiner doesn't care if the local team server's account is in the ACL. It is
    // it's caller's responsibility to check that.
    @Test
    public void joinStore_shouldJoinEvenIfACLHasNoTeamServer()
            throws Exception
    {
        Map<UserID, Role> srps = Maps.newHashMap();
        srps.put(userID, Role.OWNER);

        msj.joinStore_(sidx, rootSID, "test", srps, t);

        verify(sc).createRootStore_(eq(rootSID), any(Path.class), eq(t));
    }

    @Test
    public void joinStore_shouldNotJoinIfACLHasMoreThanOneUser()
            throws Exception
    {
        Map<UserID, Role> srps = newSRPWithTeamServers();
        srps.put(userID, Role.OWNER);
        srps.put(UserID.fromInternal("hahha@hoho"), Role.OWNER);

        msj.joinStore_(sidx, rootSID, "test", srps, t);

        verifyZeroInteractions(sc);
    }

    @Test
    public void joinStore_shouldNotJoinIfUserIsNotOwner()
            throws Exception
    {
        Map<UserID, Role> srps = newSRPWithTeamServers();
        srps.put(userID, Role.EDITOR);

        msj.joinStore_(sidx, rootSID, "test", srps, t);

        verifyZeroInteractions(sc);
    }

    @Test
    public void joinStore_shouldNotJoinIfUserMismatch()
            throws Exception
    {
        Map<UserID, Role> srps = newSRPWithTeamServers();
        srps.put(UserID.fromInternal("no.such@gmail"), Role.OWNER);

        msj.joinStore_(sidx, rootSID, "test", srps, t);

        verifyZeroInteractions(sc);
    }

    private Map<UserID, Role> newSRPWithTeamServers()
    {
        Map<UserID, Role> srps = Maps.newHashMap();
        srps.put(tsUserID1, Role.EDITOR);
        srps.put(tsUserID2, Role.EDITOR);
        return srps;
    }
}
