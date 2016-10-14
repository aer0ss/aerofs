/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.proto.Sp.PBSharedFolder;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserPermissionsAndState;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

public class TestSP_ListUserLeftSharedFolders extends AbstractSPFolderTest
{
    @Test
    public void shouldThrowExNoPermIfNonAdminQueriesOtherUsers()
            throws Exception
    {
        Organization org;
        User admin, user1, user2;

        sqlTrans.begin();
        try {
            admin = saveUser();
            org = admin.getOrganization();
            user1 = saveUser();
            user1.setOrganization(org, AuthorizationLevel.USER);
            user2 = saveUser();
            user2.setOrganization(org, AuthorizationLevel.USER);
            sqlTrans.commit();
        } catch (Exception e) {
            sqlTrans.handleException();
            throw e;
        }

        setSession(user1);

        try {
            service.listUserLeftSharedFolders(user2.id().getString());
            fail();
        } catch (ExNoPerm ignored) {
            // expected
        }
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermIfNonAdminQueriesNonExistingUsers()
            throws Exception
    {
        setSession(USER_1);
        service.listUserLeftSharedFolders("non-existing");
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermIfAdminQueriesNonExistingUsers()
            throws Exception
    {
        setSession(USER_1);
        service.listUserLeftSharedFolders("non-existing");
    }

    @Test
    public void shouldThrowExNoPermIfAdminQueriesUsersInOtherOrg()
            throws Exception
    {
        User user1, user2;

        sqlTrans.begin();
        try {
            user1 = saveUser();
            user2 = saveUserWithNewOrganization();
            sqlTrans.commit();
        } catch (Exception e) {
            sqlTrans.handleException();
            throw e;
        }

        setSession(user1);
        try {
            service.listUserLeftSharedFolders(user2.id().getString());
            fail();
        } catch (ExNoPerm ignored) {
            // expected
        }
    }

    @Test
    public void shouldNotListRootStores()
            throws Exception
    {
        createLeftFolders();

        for (PBSharedFolder sf : getLeftFoldersForAllUsers()) {
            assertFalse(new SID(BaseUtil.fromPB(sf.getStoreId())).isUserRoot());
        }
    }

    @Test
    public void shouldNotListTeamServers()
            throws Exception
    {
        createLeftFolders();

        for (PBSharedFolder sf : getLeftFoldersForAllUsers()) {
            for (PBUserPermissionsAndState urs : sf.getUserPermissionsAndStateList()) {
                assertFalse(UserID.fromInternal(urs.getUser().getUserEmail()).isTeamServerID());
            }
        }
    }

    @Test
    public void shouldOnlyListSharedFoldersOfSpecifiedUser()
            throws Exception
    {
        createLeftFolders();
        assertAllLeftFoldersHaveUser(getLeftFoldersForUser2(), USER_2);
    }

    @Test
    public void shouldReturnLeftFoldersOnly()
            throws Exception
    {
        sqlTrans.begin();
        User admin = saveUser();
        admin.setOrganization(USER_2.getOrganization(), AuthorizationLevel.ADMIN);
        sqlTrans.commit();

        setSession(admin);

        SID joinedSID = SID.generate();
        shareAndJoinFolder(USER_1, joinedSID, USER_2, Permissions.allOf(Permission.WRITE));
        assertEquals(0, getLeftFoldersForUser2().size());

        SID pendingSID = SID.generate();
        shareFolder(USER_1, pendingSID, USER_2, Permissions.allOf(Permission.WRITE));
        assertEquals(0, getLeftFoldersForUser2().size());

        leaveSharedFolder(USER_2, joinedSID);

        assertEquals(1, getLeftFoldersForUser2().size());
    }

    @Test
    public void shouldSetOwnedByTeamFlagIfAndOnlyIfOwnedByTeam()
            throws Exception
    {
        User user1, user2, user3, otherAdmin;

        sqlTrans.begin();
        try {
            user1 = saveUser();
            user2 = saveUserWithNewOrganization();
            user3 = saveUserWithNewOrganization();
            otherAdmin = saveUser();
            otherAdmin.setOrganization(user2.getOrganization(), AuthorizationLevel.ADMIN);
            sqlTrans.commit();
        } catch (Exception e) {
            sqlTrans.handleException();
            throw e;
        }

        SID sid1 = SID.generate();
        SID sid2 = SID.generate();
        shareAndJoinFolder(user1, sid1, user2, Permissions.allOf(Permission.WRITE));
        shareAndJoinFolder(user2, sid2, user3, Permissions.allOf(Permission.WRITE));

        setSession(otherAdmin);
        for (PBSharedFolder sf : service.listUserLeftSharedFolders(user2.id().getString())
                .get().getSharedFolderList()) {
            SID sid = new SID(BaseUtil.fromPB(sf.getStoreId()));
            if (sid1.equals(sid)) assertFalse(sf.getOwnedByTeam());
            if (sid2.equals(sid)) assertTrue(sf.getOwnedByTeam());
        }
    }

    private void assertAllLeftFoldersHaveUser(Collection<PBSharedFolder> sfs, User user)
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

    private void createLeftFolders()
            throws Exception
    {
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        shareAndJoinFolder(USER_2, SID_2, USER_3, Permissions.allOf(Permission.WRITE));

        leaveSharedFolder(USER_2, SID_1);
        leaveSharedFolder(USER_3, SID_2);

        setSession(USER_1);

        // add user 2 to the org
        sqlTrans.begin();
        USER_2.setOrganization(USER_1.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();
    }

    private List<PBSharedFolder> getLeftFoldersForUser2()
            throws Exception
    {
        return service.listUserLeftSharedFolders(USER_2.id().getString()).get().getSharedFolderList();
    }

    private List<PBSharedFolder> getLeftFoldersForUser1()
            throws Exception
    {
        return service.listUserLeftSharedFolders(USER_1.id().getString()).get().getSharedFolderList();
    }

    private List<PBSharedFolder> getLeftFoldersForAllUsers()
            throws Exception
    {
        List<PBSharedFolder> list = Lists.newArrayList();
        setSession(USER_1);
        list.addAll(getLeftFoldersForUser1());
        list.addAll(getLeftFoldersForUser2());
        return list;
    }
}
