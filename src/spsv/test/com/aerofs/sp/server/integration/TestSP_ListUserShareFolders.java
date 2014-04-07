/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Sp.PBSharedFolder;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserPermissionsAndState;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSP_ListUserShareFolders extends AbstractSPFolderTest
{
    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermIfNonAdminQueriesOtherUsers()
            throws Exception
    {
        setSession(USER_1);
        service.listUserSharedFolders(USER_2.id().getString());
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermIfNonAdminQueriesNonExistingUsers()
            throws Exception
    {
        setSession(USER_1);
        service.listUserSharedFolders("non-existing");
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermIfAdminQueriesNonExistingUsers()
            throws Exception
    {
        setSession(USER_1);
        service.listUserSharedFolders("non-existing");
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermIfAdminQueriesUsersInOtherOrg()
            throws Exception
    {
        setSession(USER_1);
        service.listUserSharedFolders(USER_2.id().getString());
    }

    @Test
    public void shouldNotListRootStores()
            throws Exception
    {
        createSharedFolders();

        for (PBSharedFolder sf : queryCurrentAndOtherUsers()) {
            assertFalse(new SID(sf.getStoreId()).isUserRoot());
        }
    }

    @Test
    public void shouldNotListTeamServers()
            throws Exception
    {
        createSharedFolders();

        for (PBSharedFolder sf : queryCurrentAndOtherUsers()) {
            for (PBUserPermissionsAndState urs : sf.getUserPermissionsAndStateList()) {
                assertFalse(UserID.fromInternal(urs.getUser().getUserEmail()).isTeamServerID());
            }
        }
    }

    @Test
    public void shouldOnlyListSharedFoldersOfSpecifiedUser()
            throws Exception
    {
        createSharedFolders();

        assertAllSharedFoldersHaveUser(queryOtherUser(), USER_2);

        assertAllSharedFoldersHaveUser(queryCurrentUser(), USER_1);
    }

    @Test
    public void shouldSetOwnedByTeamFlagIfAndOnlyIfOwnedByTeam()
            throws Exception
    {
        SID sid1 = SID.generate();
        SID sid2 = SID.generate();
        shareAndJoinFolder(USER_1, sid1, USER_2, Permissions.allOf(Permission.WRITE));
        shareAndJoinFolder(USER_2, sid2, USER_3, Permissions.allOf(Permission.WRITE));

        // add an admin to USER_2's team
        sqlTrans.begin();
        User admin = saveUser();
        admin.setOrganization(USER_2.getOrganization(), AuthorizationLevel.ADMIN);
        sqlTrans.commit();

        setSession(admin);
        for (PBSharedFolder sf : service.listUserSharedFolders(USER_2.id().getString()).get().getSharedFolderList()) {
            SID sid = new SID(sf.getStoreId());
            if (sid1.equals(sid)) assertFalse(sf.getOwnedByTeam());
            if (sid2.equals(sid)) assertTrue(sf.getOwnedByTeam());
        }
    }

    private void assertAllSharedFoldersHaveUser(Collection<PBSharedFolder> sfs, User user)
    {
        assertFalse(sfs.isEmpty());

        for (PBSharedFolder sf : sfs) {
            boolean hasUser = false;
            for (PBUserPermissionsAndState urs : sf.getUserPermissionsAndStateList()) {
                if (UserID.fromInternal(urs.getUser().getUserEmail()).equals(user.id())) {
                    hasUser = true;
                }
            }
            assertTrue(hasUser);
        }
    }

    private void createSharedFolders()
            throws Exception
    {
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        shareAndJoinFolder(USER_2, SID_2, USER_3, Permissions.allOf(Permission.WRITE));

        setSession(USER_1);

        // add user 2 to the org
        sqlTrans.begin();
        USER_2.setOrganization(USER_1.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();
    }

    private List<PBSharedFolder> queryOtherUser()
            throws Exception
    {
        setSession(USER_1);

        return service.listUserSharedFolders(USER_2.id().getString()).get().getSharedFolderList();
    }

    private List<PBSharedFolder> queryCurrentUser()
            throws Exception
    {
        setSession(USER_1);

        return service.listUserSharedFolders(USER_1.id().getString()).get().getSharedFolderList();
    }

    private List<PBSharedFolder> queryCurrentAndOtherUsers()
            throws Exception
    {
        List<PBSharedFolder> list = Lists.newArrayList();
        list.addAll(queryCurrentUser());
        list.addAll(queryOtherUser());
        return list;
    }
}
