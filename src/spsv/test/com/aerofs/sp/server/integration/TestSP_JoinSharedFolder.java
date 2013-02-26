/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.SID;
import com.aerofs.lib.Param;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.proto.Common.PBFolderInvitation;
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
public class TestSP_JoinSharedFolder extends AbstractSPFolderPermissionTest
{
    Set<String> published;

    @Before
    public void setupTestSPShareFolder()
    {
        published = mockAndCaptureVerkehrPublish();
    }

    @Test
    public void shouldJoinFolder() throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);

        assertEquals(1, published.size());
        assertTrue(published.contains(Param.ACL_CHANNEL_TOPIC_PREFIX + USER_1.toString()));
        published.clear();

        joinSharedFolder(USER_2, TEST_SID_1);

        assertEquals(2, published.size());
        assertTrue(published.contains(Param.ACL_CHANNEL_TOPIC_PREFIX + USER_1.toString()));
        assertTrue(published.contains(Param.ACL_CHANNEL_TOPIC_PREFIX + USER_2.toString()));
    }

    @Test
    public void shouldThrowExAlreadyExistWhenJoinFolderTwice() throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);
        joinSharedFolder(USER_2, TEST_SID_1);
        published.clear();

        try {
            joinSharedFolder(USER_2, TEST_SID_1);
            fail();
        } catch (ExAlreadyExist e) {
            sqlTrans.handleException();
        }
        assertTrue(published.isEmpty());
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
        assertTrue(published.isEmpty());
    }

    @Test
    public void shouldThrowExNoPermWhenTryingToJoinWithoutBeingInvited() throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);
        published.clear();

        try {
            joinSharedFolder(USER_3, TEST_SID_1);
            fail();
        } catch (ExNoPerm e) {
            sqlTrans.handleException();
        }
        assertTrue(published.isEmpty());
    }

    @Test
    public void shouldIgnoreInvitation() throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);

        setSessionUser(USER_2);
        PBFolderInvitation inv = service.listPendingFolderInvitations().get().getInvitation(0);
        assertEquals(USER_1.toString(), inv.getSharer());
        assertEquals(TEST_SID_1, new SID(inv.getShareId()));

        service.ignoreSharedFolderInvitation(inv.getShareId());
    }

    @Test
    public void shouldThrowExNoPermWhenLastAdminTriesToIgnoreInvitation() throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);

        setSessionUser(USER_1);
        service.leaveSharedFolder(TEST_SID_1.toPB());

        try {
            service.ignoreSharedFolderInvitation(TEST_SID_1.toPB());
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
            service.ignoreSharedFolderInvitation(TEST_SID_1.toPB());
            fail();
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }
    }

    @Test
    public void shouldThrowExNoPermWhenTryingToIgnoreInvitationWithoutBeingInvited() throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);

        setSessionUser(USER_3);
        try {
            service.ignoreSharedFolderInvitation(TEST_SID_1.toPB());
            fail();
        } catch (ExNoPerm e) {
            sqlTrans.handleException();
        }
    }

    @Test
    public void shouldThrowExNotFoundWhenTryingToIgnoreAlreadyAcceptedInvitation() throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);

        setSessionUser(USER_1);
        try {
            service.ignoreSharedFolderInvitation(TEST_SID_1.toPB());
            fail();
        } catch (ExAlreadyExist e) {
            sqlTrans.handleException();
        }
    }
}
