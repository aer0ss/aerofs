/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.proto.Sp.PBSharedFolder;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserAndRole;
import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * This class doesn't test the ability to set ACL entries (which is is done by TestSP_ACL)
 */
public class TestSP_ListUserShareFolders extends AbstractSPFolderPermissionTest
{
    @Before
    public void setup()
    {
        mockAndCaptureVerkehrPublish();
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermIfNonAdminQueriesOtherUsers()
            throws Exception
    {
        setSessionUser(USER_1);

        service.listUserSharedFolders(USER_2.getID());
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermIfNonAdminQueriesNonExistingUsers()
            throws Exception
    {
        setSessionUser(USER_1);

        service.listUserSharedFolders("non-existing");
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermIfAdminQueriesNonExistingUsers()
            throws Exception
    {
        setSessionUser(USER_1);

        addOrganization();

        service.listUserSharedFolders("non-existing");
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermIfAdminQueriesUsersInOtherOrg()
            throws Exception
    {
        setSessionUser(USER_1);

        addOrganization();

        service.listUserSharedFolders(USER_2.getID());
    }

    @Test
    public void shouldNotListRootStores()
            throws Exception
    {
        createSharedFolders();

        for (PBSharedFolder sf : queryCurrentAndOtherUsers()) {
            assertFalse(new SID(sf.getStoreId()).isRoot());
        }
    }

    @Test
    public void shouldNotListTeamServers()
            throws Exception
    {
        createSharedFolders();

        for (PBSharedFolder sf : queryCurrentAndOtherUsers()) {
            for (PBUserAndRole ur : sf.getUserAndRoleList()) {
                assertFalse(UserID.fromInternal(ur.getUser().getUserEmail()).isTeamServerID());
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

    private void assertAllSharedFoldersHaveUser(Collection<PBSharedFolder> sfs, UserID userID)
    {
        assertFalse(sfs.isEmpty());

        for (PBSharedFolder sf : sfs) {
            boolean hasUser = false;
            for (PBUserAndRole ur : sf.getUserAndRoleList()) {
                if (UserID.fromInternal(ur.getUser().getUserEmail()).equals(userID)) {
                    hasUser = true;
                }
            }
            assertTrue(hasUser);
        }
    }

    private void createSharedFolders()
            throws Exception
    {
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);

        shareAndJoinFolder(USER_2, TEST_SID_2, USER_3, Role.EDITOR);

        // make user 1 an org admin
        setSessionUser(USER_1);
        addOrganization();

        // add user 2 to the org
        sqlTrans.begin();
        factUser.create(USER_2).setOrganization(factUser.create(USER_1).getOrganization());
        sqlTrans.commit();
    }

    private List<PBSharedFolder> queryOtherUser()
            throws Exception
    {
        setSessionUser(USER_1);

        return service.listUserSharedFolders(USER_2.getID()).get().getSharedFolderList();
    }

    private List<PBSharedFolder> queryCurrentUser()
            throws Exception
    {
        setSessionUser(USER_1);

        return service.listUserSharedFolders(USER_1.getID()).get().getSharedFolderList();
    }

    private List<PBSharedFolder> queryCurrentAndOtherUsers()
            throws Exception
    {
        List<PBSharedFolder> list = Lists.newArrayList();
        list.addAll(queryCurrentUser());
        list.addAll(queryOtherUser());
        return list;
    }

    private void addOrganization() throws Exception
    {
        service.addOrganization("test org", null, StripeCustomerID.TEST.getID());
    }
}
