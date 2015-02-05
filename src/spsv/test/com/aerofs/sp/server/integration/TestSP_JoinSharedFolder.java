/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
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
        shareFolder(USER_1, SID_1, USER_2, Permissions.EDITOR);

        assertVerkehrPublishedOnlyTo(USER_1);
        clearPublishedMessages();

        joinSharedFolder(USER_2, SID_1);

        assertVerkehrPublishedOnlyTo(USER_1, USER_2);
    }

    @Test
    public void shouldSendNotificationEmail() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.EDITOR);

        joinSharedFolder(USER_2, SID_1);

        verify(sharedFolderNotificationEmailer).sendInvitationAcceptedNotificationEmail(
                factSharedFolder.create(SID_1), USER_1, USER_2);
    }

    @Test
    public void shouldPassWhenJoinFolderTwice() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.EDITOR);
        joinSharedFolder(USER_2, SID_1);

        sqlTrans.begin();
        assertEquals(factSharedFolder.create(SID_1).getStateNullable(USER_2),
                SharedFolderState.JOINED);
        sqlTrans.commit();

        clearPublishedMessages();
        joinSharedFolder(USER_2, SID_1);
        assertNothingPublished();
    }

    @Test
    public void shouldAllowLeaveAndRejoin() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.EDITOR);
        joinSharedFolder(USER_2, SID_1);
        leaveSharedFolder(USER_2, SID_1);
        clearPublishedMessages();

        sqlTrans.begin();
        assertEquals(factSharedFolder.create(SID_1).getStateNullable(USER_2),
                SharedFolderState.LEFT);
        sqlTrans.commit();

        joinSharedFolder(USER_2, SID_1);

        assertVerkehrPublishedOnlyTo(USER_1, USER_2);

        sqlTrans.begin();
        assertEquals(factSharedFolder.create(SID_1).getStateNullable(USER_2),
                SharedFolderState.JOINED);
        sqlTrans.commit();
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
        assertNothingPublished();
    }

    @Test
    public void shouldThrowWhenTryingToJoinWithoutBeingInvited() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.EDITOR);
        clearPublishedMessages();

        try {
            joinSharedFolder(USER_3, SID_1);
            fail();
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }
        assertNothingPublished();
    }

    @Test
    public void shouldIgnoreInvitation() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.EDITOR);

        setSession(USER_2);
        PBFolderInvitation inv = service.listPendingFolderInvitations().get().getInvitation(0);
        assertEquals(USER_1.id().getString(), inv.getSharer());
        assertEquals(SID_1, new SID(BaseUtil.fromPB(inv.getShareId())));

        service.ignoreSharedFolderInvitation(inv.getShareId());
    }

    @Test
    public void shouldThrowWhenTryingToIgnoreInvitationToNonExistingFolder() throws Exception
    {
        setSession(USER_1);
        try {
            service.ignoreSharedFolderInvitation(BaseUtil.toPB(SID_1));
            fail();
        } catch (ExNotFound e) {}
    }

    @Test
    public void shouldThrowWhenTryingToIgnoreInvitationWithoutBeingInvited() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.EDITOR);

        setSession(USER_3);
        try {
            service.ignoreSharedFolderInvitation(BaseUtil.toPB(SID_1));
            fail();
        } catch (ExNotFound e) {}
    }

    @Test
    public void shouldThrowWhenTryingToIgnoreAlreadyAcceptedInvitation() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.EDITOR);

        setSession(USER_1);
        try {
            service.ignoreSharedFolderInvitation(BaseUtil.toPB(SID_1));
            fail();
        } catch (ExNotFound e) {}
    }
}
