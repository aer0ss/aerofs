/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.SID;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
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
        assertTrue(published.contains(USER_1.toString()));
        published.clear();

        joinSharedFolder(USER_2, TEST_SID_1);

        assertEquals(2, published.size());
        assertTrue(published.contains(USER_1.toString()));
        assertTrue(published.contains(USER_2.toString()));
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
            trans.handleException();
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
            trans.handleException();
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
            trans.handleException();
        }
        assertTrue(published.isEmpty());
    }
}
