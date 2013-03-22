/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.SID;
import com.aerofs.lib.acl.Role;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.proto.Common.PBFolderInvitation;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

public class TestSP_JoinSharedFolder extends AbstractSPFolderTest
{
    @Test
    public void shouldJoinFolder() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        assertVerkehrPublishOnlyContains(USER_1);
        clearVerkehrPublish();

        joinSharedFolder(USER_2, SID_1);

        assertVerkehrPublishOnlyContains(USER_1, USER_2);
    }

    @Test
    public void shouldThrowExAlreadyExistWhenJoinFolderTwice() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);
        joinSharedFolder(USER_2, SID_1);
        clearVerkehrPublish();

        try {
            joinSharedFolder(USER_2, SID_1);
            fail();
        } catch (ExAlreadyExist e) {
            sqlTrans.handleException();
        }
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void shouldThrowExNotFoundWhenTryingToJoinNonExistingFolder() throws Exception
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
    public void shouldThrowExNoPermWhenTryingToJoinWithoutBeingInvited() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);
        clearVerkehrPublish();

        try {
            joinSharedFolder(USER_3, SID_1);
            fail();
        } catch (ExNoPerm e) {
            sqlTrans.handleException();
        }
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void shouldIgnoreInvitation() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        setSessionUser(USER_2);
        PBFolderInvitation inv = service.listPendingFolderInvitations().get().getInvitation(0);
        assertEquals(USER_1.id().getString(), inv.getSharer());
        assertEquals(SID_1, new SID(inv.getShareId()));

        service.ignoreSharedFolderInvitation(inv.getShareId());
    }

    @Test
    public void shouldThrowExNoPermWhenLastAdminTriesToIgnoreInvitation() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        setSessionUser(USER_1);

        // Leave first to avoid the "You have already accepted this invitation" error
        service.leaveSharedFolder(SID_1.toPB());

        try {
            service.ignoreSharedFolderInvitation(SID_1.toPB());
            fail();
        } catch (ExNoPerm e) {
            sqlTrans.handleException();
        }
    }

    @Test
    public void shouldThrowExNotFoundWhenTryingToIgnoreInvitationToNonExistingFolder() throws Exception
    {
        setSessionUser(USER_1);
        try {
            service.ignoreSharedFolderInvitation(SID_1.toPB());
            fail();
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }
    }

    @Test
    public void shouldThrowExNoPermWhenTryingToIgnoreInvitationWithoutBeingInvited() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        setSessionUser(USER_3);
        try {
            service.ignoreSharedFolderInvitation(SID_1.toPB());
            fail();
        } catch (ExNoPerm e) {
            sqlTrans.handleException();
        }
    }

    @Test
    public void shouldThrowExNotFoundWhenTryingToIgnoreAlreadyAcceptedInvitation() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        setSessionUser(USER_1);
        try {
            service.ignoreSharedFolderInvitation(SID_1.toPB());
            fail();
        } catch (ExAlreadyExist e) {
            sqlTrans.handleException();
        }
    }
}
