/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.ListSharedFoldersReply.PBSharedFolder;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;

/**
 * Test basic functionality and permission enforcement of SP's shareFolder call, but don't test its
 * ability to set ACL entries (that testing is done by TestSP_ACL)
 */
public class TestSP_ListShareFolders extends AbstractSPFolderPermissionTest
{
    @Before
    public void setup()
    {
        mockVerkehrToSuccessfullyPublishAndStoreSubscribers();
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermForNonAdmin()
            throws Exception
    {
        service.listSharedFolders(1000, 0);
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
    public void shouldNotListTeamServerACLs()
            throws Exception
    {
        for (PBSharedFolder sf : createAndListTwoSharedFolders()) {
            for (PBSubjectRolePair srp : sf.getSubjectRoleList()) {
                assertFalse(UserID.fromInternal(srp.getSubject()).isTeamServerID());
            }
        }
    }

    @Test
    public void shouldListSharerAsOwner()
            throws Exception
    {
        for (PBSharedFolder sf : createAndListTwoSharedFolders()) {
            boolean hasOwner = false;
            for (PBSubjectRolePair srp : sf.getSubjectRoleList()) {
                if (UserID.fromInternal(srp.getSubject()).equals(TEST_USER_1)) {
                    assertEquals(Role.fromPB(srp.getRole()), Role.OWNER);
                    assertFalse(hasOwner);
                    hasOwner = true;
                }
            }
            assert hasOwner;
        }
    }

    @Test
    public void shouldListSharee()
            throws Exception
    {
        for (PBSharedFolder sf : createAndListTwoSharedFolders()) {
            boolean hasSharee = false;
            for (PBSubjectRolePair srp : sf.getSubjectRoleList()) {
                if (!UserID.fromInternal(srp.getSubject()).equals(TEST_USER_1)) {
                    assertEquals(Role.fromPB(srp.getRole()), Role.EDITOR);
                    assertFalse(hasSharee);
                    hasSharee = true;
                }
            }
            assert hasSharee;
        }
    }

    private List<PBSharedFolder> createAndListTwoSharedFolders()
            throws Exception
    {
        // for backward compat with existing tests, accept invite immediately to update ACLs
        shareAndJoinFolder(TEST_USER_1, TEST_SID_1, TEST_USER_2, Role.EDITOR);

        // for backward compat with existing tests, accept invite immediately to update ACLs
        shareAndJoinFolder(TEST_USER_1, TEST_SID_2, TEST_USER_3, Role.EDITOR);

        // add a new org so user 1 can haz permissions to list folders
        service.addOrganization("test org");

        return service.listSharedFolders(100, 0).get().getSharedFoldersList();
    }

}
