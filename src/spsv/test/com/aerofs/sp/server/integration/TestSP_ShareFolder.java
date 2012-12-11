/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.lib.FullName;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
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
                        eq(sharer.toString()), eq(sid.toStringFormal()), eq(""), anyString());
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
        published = mockVerkehrToSuccessfullyPublishAndStoreSubscribers();
    }

    @Test
    public void shouldThrowWhenTryingToShareWithSelf()
            throws Exception
    {
        shareFolder(TEST_USER_1, TEST_SID_1, TEST_USER_1, Role.EDITOR);


    }

    @Test
    public void shouldSuccessfullyShareFolderWithOneUser()
            throws Exception
    {
        shareFolder(TEST_USER_1, TEST_SID_1, TEST_USER_2, Role.EDITOR);

        assertEquals(1, published.size());
        assertTrue(published.contains(TEST_USER_1.toString()));
        verifyFolderInvitation(TEST_USER_1, TEST_USER_2, TEST_SID_1, true);
        verifyNewUserAccountInvitation(TEST_USER_1, TEST_USER_2, TEST_SID_1, false);
    }

    @Test
    public void shouldInviteNonAeroFSUserWhenSharingAFolderWithThem()
            throws Exception
    {
        // user 4 hasn't actually been added to the db yet so this should trigger an invite to them
        shareFolder(TEST_USER_1, TEST_SID_1, TEST_USER_4, Role.EDITOR);

        assertEquals(1, published.size());
        assertTrue(published.contains(TEST_USER_1.toString()));
        verifyFolderInvitation(TEST_USER_1, TEST_USER_4, TEST_SID_1, false);
        verifyNewUserAccountInvitation(TEST_USER_1, TEST_USER_4, TEST_SID_1, true);
    }

    @Test
    public void shouldJoinFolder() throws Exception
    {
        shareFolder(TEST_USER_1, TEST_SID_1, TEST_USER_2, Role.EDITOR);

        assertEquals(1, published.size());
        assertTrue(published.contains(TEST_USER_1.toString()));
        published.clear();

        String code = getSharedFolderCode(TEST_USER_1, TEST_SID_1, TEST_USER_2);
        Assert.assertNotNull(code);

        joinSharedFolder(TEST_USER_2, code);

        assertEquals(2, published.size());
        assertTrue(published.contains(TEST_USER_1.toString()));
        assertTrue(published.contains(TEST_USER_2.toString()));
    }

    @Test
    public void shouldThrowExNotFoundWhenTryingToJoinWithInvalidCode() throws Exception
    {
        boolean ok = false;
        try {
            joinSharedFolder(TEST_USER_2, "deadbeef");
        } catch (ExNotFound e) {
            ok = true;
        }
        Assert.assertTrue(ok);
        assertTrue(published.isEmpty());
    }

    @Test
    public void shouldThrowExNoPermWhenTryingToJoinWithCodeForDifferentUser() throws Exception
    {
        shareFolder(TEST_USER_1, TEST_SID_1, TEST_USER_2, Role.EDITOR);
        published.clear();

        String code = getSharedFolderCode(TEST_USER_1, TEST_SID_1, TEST_USER_2);
        Assert.assertNotNull(code);

        boolean ok = false;
        try {
            joinSharedFolder(TEST_USER_3, code);
        } catch (ExNoPerm e) {
            ok = true;
        }
        Assert.assertTrue(ok);
        assertTrue(published.isEmpty());
    }

    @Test
    public void shouldThrowExNoPermWhenEditorTriesToInviteToFolder()
            throws Exception
    {
        shareAndJoinFolder(TEST_USER_1, TEST_SID_1, TEST_USER_2, Role.EDITOR);
        published.clear();

        boolean ok = false;
        try {
            // should throw ExNoPerm because user 2 is an editor
            shareFolder(TEST_USER_2, TEST_SID_1, TEST_USER_3, Role.EDITOR);
        } catch (ExNoPerm e) {
            trans.handleException();
            ok = true;
        }
        Assert.assertTrue(ok);
        assertTrue(published.isEmpty());
    }

    @Test
    public void shouldThrowExNotFoundWhenUnverifiedUserTriesToShareFolder()
            throws Exception
    {
        // add user 4 to db but don't verify their account
        trans.begin();
        udb.addUser(TEST_USER_4, new FullName(TEST_USER_4.toString(), TEST_USER_4.toString()),
                TEST_USER_4_CRED, OrgID.DEFAULT, AuthorizationLevel.USER);
        trans.commit();

        shareFolder(TEST_USER_1, TEST_SID_1, TEST_USER_4, Role.OWNER);
        published.clear();

        boolean ok = false;
        try {
            // should throw ExNoPerm because user 4 is unverified
            shareFolder(TEST_USER_4, TEST_SID_1, TEST_USER_2, Role.EDITOR);
        } catch (ExNoPerm e) {
            trans.handleException();
            ok = true;
        }
        Assert.assertTrue(ok);
        assertTrue(published.isEmpty());
    }
}
