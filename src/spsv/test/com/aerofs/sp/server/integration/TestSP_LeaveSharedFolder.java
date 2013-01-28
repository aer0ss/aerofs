/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.SID;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNotFound;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * Test basic functionality and permission enforcement of SP's shareFolder call, but don't test its
 * ability to set ACL entries (that testing is done by TestSP_ACL)
 */
public class TestSP_LeaveSharedFolder extends AbstractSPFolderPermissionTest
{
    Set<String> published;

    @Before
    public void setupTestSPShareFolder()
    {
        published = mockAndCaptureVerkehrPublish();
    }

    @Test
    public void shouldAllowMemberToLeaveShareFolder() throws Exception
    {
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);
        published.clear();

        leaveSharedFolder(USER_2, TEST_SID_1);
        assertEquals(2, published.size());
        assertTrue(published.contains(USER_1.toString()));
        assertTrue(published.contains(USER_2.toString()));
    }

    @Test
    public void shouldAllowAdminToLeaveShareFolder() throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);

        leaveSharedFolder(USER_1, TEST_SID_1);
    }

    @Test
    public void shouldAllowPendingMemberToLeaveShareFolder() throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);
        published.clear();

        leaveSharedFolder(USER_2, TEST_SID_1);
        assertTrue(published.isEmpty());
    }

    @Test
    public void shouldThrowExBadArgsWhenTryingToLeaveRootStore() throws Exception
    {
        try {
            leaveSharedFolder(USER_1, SID.rootSID(USER_1));
            fail();
        } catch (ExBadArgs e) {
            sqlTrans.handleException();
        }
        assertTrue(published.isEmpty());
    }

    @Test
    public void shouldThrowExNotFoundWhenNonMemberTriesToLeaveShareFolder() throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);
        published.clear();

        try {
            leaveSharedFolder(USER_3, TEST_SID_1);
            fail();
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }
        assertTrue(published.isEmpty());
    }

    @Test
    public void shouldThrowExNotFoundWhenTryingToLeaveNonExistingSharedFolder() throws Exception
    {
        try {
            leaveSharedFolder(USER_1, TEST_SID_1);
            fail();
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }
        assertTrue(published.isEmpty());
    }
}
