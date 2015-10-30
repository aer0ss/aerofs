/*
 * Copyright (c) Air Computing Inc., 2012.
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
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TestSP_ListOrganizationShareFolders extends AbstractSPFolderTest
{
    @Test
    public void shouldListSharedFoldersWithPrefix()
            throws Exception
    {
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        shareAndJoinFolder(USER_1, SID_2, USER_3, Permissions.allOf(Permission.WRITE));

        setSession(USER_1);
        String folderName =  SID_2.toStringFormal();

        List<PBSharedFolder> matches = service
                .listOrganizationSharedFolders(100, 0, folderName.substring(0,folderName.length() - 2))
                .get()
                .getSharedFolderList();

        assertEquals(1, matches.size());
        assertEquals(folderName, matches.get(0).getName());
    }

    @Test
    public void shouldThrowExNoPermForNonAdmin()
            throws Exception
    {
        setSession(USER_1);

        // make USER_1 a non-admin
        sqlTrans.begin();
        USER_2.setLevel(AuthorizationLevel.ADMIN);
        USER_1.setOrganization(USER_2.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();

        try {
            service.listOrganizationSharedFolders(1000, 0, null);
            fail();
        } catch (ExNoPerm ignored) {
            // expected
        }
    }

    @Test
    public void shouldNotListRootStores()
            throws Exception
    {
        for (PBSharedFolder sf : createAndListTwoSharedFolders()) {
            assertFalse(new SID(BaseUtil.fromPB(sf.getStoreId())).isUserRoot());
        }
    }

    @Test
    public void shouldNotListTeamServers()
            throws Exception
    {
        for (PBSharedFolder sf : createAndListTwoSharedFolders()) {
            for (PBUserPermissionsAndState urs: sf.getUserPermissionsAndStateList()) {
                assertFalse(UserID.fromInternal(urs.getUser().getUserEmail()).isTeamServerID());
            }
        }
    }

    @Test
    public void shouldListSharerAsOwner()
            throws Exception
    {
        for (PBSharedFolder sf : createAndListTwoSharedFolders()) {
            boolean hasOwner = false;
            for (PBUserPermissionsAndState urs : sf.getUserPermissionsAndStateList()) {
                if (UserID.fromInternal(urs.getUser().getUserEmail()).equals(USER_1.id())) {
                    assertEquals(Permissions.fromPB(urs.getPermissions()),
                            Permissions.allOf(Permission.WRITE, Permission.MANAGE));
                    assertFalse(hasOwner);
                    hasOwner = true;
                }
            }
            assertTrue(hasOwner);
        }
    }

    @Test
    public void shouldListSharee()
            throws Exception
    {
        for (PBSharedFolder sf : createAndListTwoSharedFolders()) {
            boolean hasSharee = false;
            for (PBUserPermissionsAndState urs : sf.getUserPermissionsAndStateList()) {
                if (!UserID.fromInternal(urs.getUser().getUserEmail()).equals(USER_1.id())) {
                    assertEquals(
                            Permissions.fromPB(urs.getPermissions()),
                            Permissions.allOf(Permission.WRITE));
                    assertFalse(hasSharee);
                    hasSharee = true;
                }
            }
            assertTrue(hasSharee);
        }
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
            otherAdmin = saveUserWithNewOrganization();
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
        for (PBSharedFolder sf : service.listOrganizationSharedFolders(100, 0, null)
                .get().getSharedFolderList()) {
            SID sid = new SID(BaseUtil.fromPB(sf.getStoreId()));
            if (sid1.equals(sid)) assertFalse(sf.getOwnedByTeam());
            if (sid2.equals(sid)) assertTrue(sf.getOwnedByTeam());
        }
    }

    private List<PBSharedFolder> createAndListTwoSharedFolders()
            throws Exception
    {
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        shareAndJoinFolder(USER_1, SID_2, USER_3, Permissions.allOf(Permission.WRITE));

        setSession(USER_1);

        return service.listOrganizationSharedFolders(100, 0, null).get().getSharedFolderList();
    }
}
