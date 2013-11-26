/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Common.PBFolderInvitation;
import com.aerofs.sp.common.SharedFolderState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;

public class TestSP_JoinSharedFolder extends AbstractSPFolderTest
{
    @Test
    public void shouldJoinFolder() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        assertVerkehrPublishOnlyContains(USER_1);
        clearVerkehrPublish();

        joinSharedFolder(USER_2, SID_1);

        assertVerkehrPublishOnlyContains(USER_1, USER_2);
    }

    @Test
    public void shouldSendNotificationEmail() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        joinSharedFolder(USER_2, SID_1);

        verify(sharedFolderNotificationEmailer).sendInvitationAcceptedNotificationEmail(
                factSharedFolder.create(SID_1), USER_1, USER_2);
    }

    @Test
    public void shouldPassWhenJoinFolderTwice() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        joinSharedFolder(USER_2, SID_1);

        sqlTrans.begin();
        assertEquals(factSharedFolder.create(SID_1).getStateNullable(USER_2),
                SharedFolderState.JOINED);
        sqlTrans.commit();

        clearVerkehrPublish();
        joinSharedFolder(USER_2, SID_1);
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void shouldThrowWhenTryingToJoinNonExistingFolder() throws Exception
    {
        try {
            joinSharedFolder(USER_2, SID.generate());
            fail();
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void shouldThrowWhenTryingToJoinWithoutBeingInvited() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        clearVerkehrPublish();

        try {
            joinSharedFolder(USER_3, SID_1);
            fail();
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void shouldIgnoreInvitation() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        setSessionUser(USER_2);
        PBFolderInvitation inv = service.listPendingFolderInvitations().get().getInvitation(0);
        assertEquals(USER_1.id().getString(), inv.getSharer());
        assertEquals(SID_1, new SID(inv.getShareId()));

        service.ignoreSharedFolderInvitation(inv.getShareId());
    }

    @Test
    public void shouldThrowWhenTryingToIgnoreInvitationToNonExistingFolder() throws Exception
    {
        setSessionUser(USER_1);
        try {
            service.ignoreSharedFolderInvitation(SID_1.toPB());
            fail();
        } catch (ExNotFound e) {}
    }

    @Test
    public void shouldThrowWhenTryingToIgnoreInvitationWithoutBeingInvited() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        setSessionUser(USER_3);
        try {
            service.ignoreSharedFolderInvitation(SID_1.toPB());
            fail();
        } catch (ExNotFound e) {}
    }

    @Test
    public void shouldThrowWhenTryingToIgnoreAlreadyAcceptedInvitation() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        setSessionUser(USER_1);
        try {
            service.ignoreSharedFolderInvitation(SID_1.toPB());
            fail();
        } catch (ExNotFound e) {}
    }
}
