/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.proto.Sp.PBSharedFolder;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserAndRole;
import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * This class doesn't test the ability to set ACL entries (which is is done by TestSP_ACL)
 */
public class TestSP_ListOrganizationShareFolders extends AbstractSPFolderPermissionTest
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
                if (UserID.fromInternal(ur.getUser().getUserEmail()).equals(USER_1)) {
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
                if (!UserID.fromInternal(ur.getUser().getUserEmail()).equals(USER_1)) {
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
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);

        shareAndJoinFolder(USER_1, TEST_SID_2, USER_3, Role.EDITOR);

        setSessionUser(USER_1);

        // add a new org so user 1 can haz permissions to list folders
        service.addOrganization("test org", null, StripeCustomerID.TEST.getString());

        return service.listOrganizationSharedFolders(100, 0).get().getSharedFolderList();
    }

}
