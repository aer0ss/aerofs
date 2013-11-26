/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.id.SID;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import org.junit.Test;

import static org.junit.Assert.fail;

public class TestSP_LeaveSharedFolder extends AbstractSPFolderTest
{
    @Test
    public void shouldAllowMemberToLeaveShareFolder() throws Exception
    {
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        clearVerkehrPublish();

        leaveSharedFolder(USER_2, SID_1);
        assertVerkehrPublishOnlyContains(USER_2, USER_1);
    }

    @Test
    public void shouldAllowAdminToLeaveShareFolder() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        leaveSharedFolder(USER_1, SID_1);
    }

    @Test
    public void shouldAllowPendingMemberToLeaveShareFolder() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
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
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
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
