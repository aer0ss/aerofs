/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.FullName;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test basic functionality and permission enforcement of SP's shareFolder call, but don't test its
 * ability to set ACL entries (that testing is done by TestSPACL)
 */
public class TestSPShareFolder extends AbstractSPFolderPermissionTest
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
                .createFolderInvitation(eq(sharer.toString()), eq(sharee.toString()),
                        eq(sharer.toString()), eq(sid.toString()),
                        eq(""), anyString());
    }

    private void verifyNewUserAccountInvitation(UserID sharer, UserID sharee, SID sid,
            boolean shouldBeInvited)
            throws Exception
    {
        verify(factEmailer, shouldBeInvited ? times(1) : never())
                .createUserInvitation(eq(sharer.toString()), eq(sharee.toString()),
                        eq(sharer.toString()), eq(sid.toString()), eq(""), anyString());
    }

    @Before
    public void setupTestSPShareFolder()
    {
        setupMockVerkehrToSuccessfullyPublish();
    }

    @Test
    public void shouldSuccessfullyShareFolderWithOneUser()
            throws Exception
    {
        shareFolder(TEST_USER_1, TEST_SID_1, TEST_USER_2, Role.EDITOR);
        verifyFolderInvitation(TEST_USER_1, TEST_USER_2, TEST_SID_1, true);
        verifyNewUserAccountInvitation(TEST_USER_1, TEST_USER_2, TEST_SID_1, false);
    }

    @Test
    public void shouldInviteNonAeroFSUserWhenSharingAFolderWithThem()
            throws Exception
    {
        // user 4 hasn't actually been added to the db yet so this should trigger an invite to them
        shareFolder(TEST_USER_1, TEST_SID_1, TEST_USER_4, Role.EDITOR);
        verifyFolderInvitation(TEST_USER_1, TEST_USER_4, TEST_SID_1, false);
        verifyNewUserAccountInvitation(TEST_USER_1, TEST_USER_4, TEST_SID_1, true);
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermWhenEditorTriesToInviteToFolder()
            throws Exception
    {
        shareFolder(TEST_USER_1, TEST_SID_1, TEST_USER_2, Role.EDITOR);

        // should throw ExNoPerm because user 2 is an editor
        shareFolder(TEST_USER_2, TEST_SID_1, TEST_USER_3, Role.EDITOR);
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowExNoPermWhenUnverifiedUserTriesToShareFolder()
            throws Exception
    {
        // add user 4 to db but don't verify their account
        transaction.begin();
        udb.addUser(TEST_USER_4, new FullName(TEST_USER_4.toString(), TEST_USER_4.toString()),
                TEST_USER_4_CRED, OrgID.DEFAULT, AuthorizationLevel.USER);
        transaction.commit();

        shareFolder(TEST_USER_1, TEST_SID_1, TEST_USER_4, Role.OWNER);

        // should throw ExNoPerm because user 4 is unverified
        shareFolder(TEST_USER_4, TEST_SID_1, TEST_USER_2, Role.EDITOR);
    }
}
