/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.lib.FullName;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test basic functionality and permission enforcement of SP's shareFolder call, but don't test its
 * ability to set ACL entries (that testing is done by TestSP_ACL)
 */
public class TestSP_ShareFolder extends AbstractSPFolderPermissionTest
{
    // don't register this one, use it to test sharing with non-AeroFS users
    private UserID TEST_USER_4 = UserID.fromInternal("user_4");
    private static final byte[] TEST_USER_4_CRED = "CREDENTIALS".getBytes();

    /**
     * Verifies that a folder invitation email would (shouldBeSent=true) or wouldn't
     * (shouldBeSent=false) have been sent
     */
    private void verifyFolderInvitation(UserID sharer, UserID sharee, SID sid, boolean shouldBeSent)
            throws Exception
    {
        verify(factEmailer, shouldBeSent ? times(1) : never())
                .createFolderInvitationEmailer(eq(sharer.toString()), eq(sharee.toString()),
                        eq(sharer.toString()), eq(sid.toStringFormal()), eq(""), any(SID.class));
    }

    private void verifyNewUserAccountInvitation(UserID sharer, UserID sharee, SID sid,
            boolean shouldBeInvited)
            throws Exception
    {
        verify(factEmailer, shouldBeInvited ? times(1) : never())
                .createSignUpInvitationEmailer(eq(sharer.toString()), eq(sharee.toString()),
                        eq(sharer.toString()), eq(sid.toStringFormal()), eq(""), anyString());
    }

    Set<String> published;

    @Before
    public void setupTestSPShareFolder()
    {
        published = mockAndCaptureVerkehrPublish();
    }

    @Test
    public void shouldThrowWhenTryingToShareWithSelf()
            throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_1, Role.EDITOR);
    }

    @Test
    public void shouldSuccessfullyShareFolderWithOneUser()
            throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);

        assertEquals(1, published.size());
        assertTrue(published.contains(USER_1.toString()));
        verifyFolderInvitation(USER_1, USER_2, TEST_SID_1, true);
        verifyNewUserAccountInvitation(USER_1, USER_2, TEST_SID_1, false);
    }

    @Test
    public void shouldInviteNonAeroFSUserWhenSharingAFolderWithThem()
            throws Exception
    {
        // user 4 hasn't actually been added to the db yet so this should trigger an invite to them
        shareFolder(USER_1, TEST_SID_1, TEST_USER_4, Role.EDITOR);

        assertEquals(1, published.size());
        assertTrue(published.contains(USER_1.toString()));
        verifyFolderInvitation(USER_1, TEST_USER_4, TEST_SID_1, false);
        verifyNewUserAccountInvitation(USER_1, TEST_USER_4, TEST_SID_1, true);
    }

    @Test
    public void shouldThrowExNoPermWhenEditorTriesToInviteToFolder()
            throws Exception
    {
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);
        published.clear();

        try {
            // should throw ExNoPerm because user 2 is an editor
            shareFolder(USER_2, TEST_SID_1, USER_3, Role.EDITOR);
            fail();
        } catch (ExNoPerm e) {
            sqlTrans.handleException();
        }
        assertTrue(published.isEmpty());
    }

    @Test
    public void shouldThrowExNotFoundWhenUnverifiedUserTriesToShareFolder()
            throws Exception
    {
        // add user 4 to db but don't verify their account
        sqlTrans.begin();
        udb.insertUser(TEST_USER_4, new FullName(TEST_USER_4.toString(), TEST_USER_4.toString()),
                TEST_USER_4_CRED, OrganizationID.DEFAULT, AuthorizationLevel.USER);
        sqlTrans.commit();

        shareFolder(USER_1, TEST_SID_1, TEST_USER_4, Role.OWNER);
        published.clear();

        try {
            // should throw ExNoPerm because user 4 is unverified
            shareFolder(TEST_USER_4, TEST_SID_1, USER_2, Role.EDITOR);
            fail();
        } catch (ExNoPerm e) {
            sqlTrans.handleException();
        }
        assertTrue(published.isEmpty());
    }

    @Test
    public void shouldThrowExBadArgsWhenTryingToShareRootStore() throws Exception
    {
        try {
            shareFolder(USER_1, SID.rootSID(USER_1), USER_2, Role.EDITOR);
            fail();
        } catch (ExBadArgs e) {
            sqlTrans.handleException();
        }
        assertTrue(published.isEmpty());
    }

    @Test
    public void shouldThrowExAlreadyExistWhenInvitingExistingMember()
            throws Exception
    {
        shareAndJoinFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);
        published.clear();

        try {
            shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);
            fail();
        } catch (ExAlreadyExist e) {
            sqlTrans.handleException();
        }
        assertTrue(published.isEmpty());
    }

    @Test
    public void shouldAllowInvitingExistingPendingUsers()
            throws Exception
    {
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);

        // the second call should not fail
        shareFolder(USER_1, TEST_SID_1, USER_2, Role.EDITOR);
    }
}
