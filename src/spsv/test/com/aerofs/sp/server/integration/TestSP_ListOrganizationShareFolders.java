/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.proto.Sp.PBSharedFolder;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserAndRole;
import com.aerofs.lib.acl.Role;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class TestSP_ListOrganizationShareFolders extends AbstractSPFolderTest
{
    @Before
    public void setup()
    {
        mockAndCaptureVerkehrPublish();
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermForNonAdmin()
            throws Exception
    {
        setSessionUser(USER_1);

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
            assertFalse(new SID(sf.getStoreId()).isRoot());
        }
    }

    @Test
    public void shouldNotListTeamServers()
            throws Exception
    {
        for (PBSharedFolder sf : createAndListTwoSharedFolders()) {
            for (PBUserAndRole ur : sf.getUserAndRoleList()) {
                assertFalse(UserID.fromInternal(ur.getUser().getUserEmail()).isTeamServerID());
            }
        }
    }

    @Test
    public void shouldListSharerAsOwner()
            throws Exception
    {
        for (PBSharedFolder sf : createAndListTwoSharedFolders()) {
            boolean hasOwner = false;
            for (PBUserAndRole ur : sf.getUserAndRoleList()) {
                if (UserID.fromInternal(ur.getUser().getUserEmail()).equals(USER_1.id())) {
                    assertEquals(Role.fromPB(ur.getRole()), Role.OWNER);
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
            for (PBUserAndRole ur : sf.getUserAndRoleList()) {
                if (!UserID.fromInternal(ur.getUser().getUserEmail()).equals(USER_1.id())) {
                    assertEquals(Role.fromPB(ur.getRole()), Role.EDITOR);
                    assertFalse(hasSharee);
                    hasSharee = true;
                }
            }
            assertTrue(hasSharee);
        }
    }

    private List<PBSharedFolder> createAndListTwoSharedFolders()
            throws Exception
    {
        shareAndJoinFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        shareAndJoinFolder(USER_1, SID_2, USER_3, Role.EDITOR);

        setSessionUser(USER_1);

        return service.listOrganizationSharedFolders(100, 0).get().getSharedFolderList();
    }

}
