/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Sp.PBSharedFolder;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserPermissionsAndState;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSP_ListOrganizationShareFolders extends AbstractSPFolderTest
{
    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermForNonAdmin()
            throws Exception
    {
        setSession(USER_1);

        // make USER_1 a non-admin
        sqlTrans.begin();
        USER_1.setOrganization(USER_2.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();

        service.listOrganizationSharedFolders(1000, 0);
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
        for (PBSharedFolder sf : service.listOrganizationSharedFolders(100, 0)
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

        return service.listOrganizationSharedFolders(100, 0).get().getSharedFolderList();
    }
}
