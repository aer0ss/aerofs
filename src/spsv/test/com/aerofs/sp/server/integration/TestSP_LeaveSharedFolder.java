/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.SID;
import com.aerofs.lib.acl.Role;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import org.junit.Test;

import static junit.framework.Assert.fail;

/**
 * Test basic functionality and permission enforcement of SP's shareFolder call, but don't test its
 * ability to set ACL entries (that testing is done by TestSP_ACL)
 */
public class TestSP_LeaveSharedFolder extends AbstractSPFolderPermissionTest
{
    @Test
    public void shouldAllowMemberToLeaveShareFolder() throws Exception
    {
        shareAndJoinFolder(USER_1, SID_1, USER_2, Role.EDITOR);
        clearVerkehrPublish();

        leaveSharedFolder(USER_2, SID_1);
        assertVerkehrPublishOnlyContains(USER_2, USER_1);
    }

    @Test
    public void shouldAllowAdminToLeaveShareFolder() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        leaveSharedFolder(USER_1, SID_1);
    }

    @Test
    public void shouldAllowPendingMemberToLeaveShareFolder() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);
        clearVerkehrPublish();

        leaveSharedFolder(USER_2, SID_1);
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void shouldThrowExBadArgsWhenTryingToLeaveRootStore() throws Exception
    {
        try {
            leaveSharedFolder(USER_1, SID.rootSID(USER_1.id()));
            fail();
        } catch (ExBadArgs e) {
            sqlTrans.handleException();
        }
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void shouldThrowExNotFoundWhenNonMemberTriesToLeaveShareFolder() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);
        clearVerkehrPublish();

        try {
            leaveSharedFolder(USER_3, SID_1);
            fail();
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void shouldThrowExNotFoundWhenTryingToLeaveNonExistingSharedFolder() throws Exception
    {
        try {
            leaveSharedFolder(USER_1, SID_1);
            fail();
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }
        assertVerkehrPublishIsEmpty();
    }
}
