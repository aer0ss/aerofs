/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExInviteeListEmpty;
import com.aerofs.lib.ex.ExNoStripeCustomerID;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestSP_ShareFolder extends AbstractSPACLTest
{
    List<Command> delivered;

    @Before
    public void setupTestSPShareFolder()
    {
        delivered = mockAndCaptureVerkehrDeliverPayload();
    }

    @Test
    public void shouldThrowWhenInviteeListEmpty()
            throws Exception
    {
        try {
            setSessionUser(USER_1);
            service.shareFolder(SID_1.toStringFormal(), SID_1.toPB(),
                    Collections.<PBSubjectRolePair>emptyList(), "", false, false).get();
            fail();
        } catch (ExInviteeListEmpty e) {}
    }

    @Test
    public void shouldNotThrowWhenInviteeListEmptyAndExternalFlagIsSet()
            throws Exception
    {
        try {
            setSessionUser(USER_1);
            service.shareFolder(SID_1.toStringFormal(), SID_1.toPB(),
                    Collections.<PBSubjectRolePair>emptyList(), "", true, false).get();
        } catch (ExInviteeListEmpty e) {
            fail();
        }
    }

    @Test
    public void shouldSuccessfullyShareFolderWithOneUser()
            throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        assertVerkehrPublishOnlyContains(USER_1);
        verifyFolderInvitation(USER_1, USER_2, SID_1, 1);
        verifyNewUserAccountInvitation(USER_1, USER_2, SID_1, false);
    }

    @Test
    public void shouldSuccessfullyShareFolderExternalWithOneUser()
            throws Exception
    {
        shareFolderExternal(USER_1, SID_1, USER_2, Role.EDITOR);

        assertVerkehrPublishOnlyContains(USER_1);
        verifyFolderInvitation(USER_1, USER_2, SID_1, 1);
        verifyNewUserAccountInvitation(USER_1, USER_2, SID_1, false);
    }

    @Test
    public void shouldInviteNonExistingUser()
            throws Exception
    {
        User user = newUser();
        // the new hasn't actually been added to the db yet so this should trigger an invite to them
        shareFolder(USER_1, SID_1, user, Role.EDITOR);

        assertVerkehrPublishOnlyContains(USER_1);
        verifyFolderInvitation(USER_1, user, SID_1, 0);
        verifyNewUserAccountInvitation(USER_1, user, SID_1, true);
    }

    @Test
    public void shouldThrowWhenEditorTriesToInviteToFolder()
            throws Exception
    {
        shareAndJoinFolder(USER_1, SID_1, USER_2, Role.EDITOR);
        clearVerkehrPublish();

        // Set USER_2 as a non-admin
        sqlTrans.begin();
        USER_2.setOrganization(USER_1.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();

        try {
            // should throw ExNoPerm because user 2 is an editor
            shareFolder(USER_2, SID_1, USER_3, Role.EDITOR);
            fail();
        } catch (ExNoPerm e) {
            sqlTrans.handleException();
        }
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void shouldThrowExBadArgsWhenTryingToShareRootStore() throws Exception
    {
        try {
            shareFolder(USER_1, SID.rootSID(USER_1.id()), USER_2, Role.EDITOR);
            fail();
        } catch (ExBadArgs e) {
            sqlTrans.handleException();
        }
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void shouldThrowExAlreadyExistWhenInvitingExistingMember()
            throws Exception
    {
        shareAndJoinFolder(USER_1, SID_1, USER_2, Role.EDITOR);
        clearVerkehrPublish();

        try {
            shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);
            fail();
        } catch (ExAlreadyExist e) {
            sqlTrans.handleException();
        }
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void shouldAllowInvitingExistingPendingUsers()
            throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        // the second call should not fail
        shareFolder(USER_1, SID_1, USER_2, Role.EDITOR);
    }

    @Test
    public void shouldSendRefreshCRLWhenJoiningSharedFolder()
            throws Exception
    {
        sqlTrans.begin();

        saveDevice(USER_1);
        saveDevice(USER_1);
        saveDevice(USER_2);

        sqlTrans.commit();

        shareAndJoinFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        // 3 refresh CRL commands.
        Assert.assertEquals(3, delivered.size());

        for (Command command : delivered) {
            Assert.assertEquals(CommandType.REFRESH_CRL, command.getType());
        }
    }

    @Test
    public void shouldAllowToShareIfNoACLExists()
            throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Role.OWNER);

        GetACLReply reply = service.getACL(0L).get();

        assertGetACLReplyIncrementsEpochBy(reply, 1);
        assertACLOnlyContains(getSingleACL(SID_1, reply), USER_1, Role.OWNER);
        assertVerkehrPublishOnlyContains(USER_1);
    }

    @Test
    public void shouldAllowOwnerToShareAndNotifyAllAffectedUsers()
            throws Exception
    {
        // create shared folder and invite a first user
        shareFolder(USER_1, SID_1, USER_2, Role.OWNER);
        assertVerkehrPublishOnlyContains(USER_1);
        clearVerkehrPublish();

        // inviteee joins
        joinSharedFolder(USER_2, SID_1);
        assertVerkehrPublishOnlyContains(USER_1, USER_2);
        clearVerkehrPublish();

        // now lets see if the other person can add a third person
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        shareAndJoinFolder(USER_2, SID_1, user, Role.EDITOR);
        assertVerkehrPublishOnlyContains(USER_1, USER_2, user);
        clearVerkehrPublish();

        // now let's see what the acls are like
        setSessionUser(USER_1);
        GetACLReply reply = service.getACL(0L).get();

        assertGetACLReplyIncrementsEpochBy(reply, 3);

        assertACLOnlyContains(getSingleACL(SID_1, reply),
                new UserAndRole(USER_1, Role.OWNER),
                new UserAndRole(USER_2, Role.OWNER),
                new UserAndRole(user, Role.EDITOR));
    }

    @Test
    public void shouldForbidNonOwnerToShare()
            throws Exception
    {
        // share folder and invite a new editor
        shareAndJoinFolder(USER_1, SID_1, USER_2, Role.EDITOR);

        sqlTrans.begin();
        // set USER_2 as non-admin
        USER_2.setOrganization(USER_1.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();

        try {
            // get the editor to try and make some role changes
            shareAndJoinFolder(USER_2, SID_1, newUser(), Role.EDITOR);
            // must not reach here
            org.junit.Assert.fail();
        } catch (ExNoPerm e) {}
    }

    @Test
    public void shouldAllowInvitingLeftUsers()
            throws Exception
    {
        sqlTrans.begin();
        User owner = saveUser();
        User leftUser = saveUser();
        sqlTrans.commit();

        SID sid = SID.generate();
        SharedFolder folder = factSharedFolder.create(sid);

        shareAndJoinFolder(owner, sid, leftUser, Role.EDITOR);
        verifyFolderInvitation(owner, leftUser, sid, 1);

        leaveSharedFolder(leftUser, sid);
        shareFolder(owner, sid, leftUser, Role.VIEWER);

        sqlTrans.begin();
        SharedFolderState state = folder.getStateNullable(leftUser);
        sqlTrans.commit();

        assertEquals(SharedFolderState.PENDING, state);

        // twice because we've verified that it's been sent once during the setup
        verifyFolderInvitation(owner, leftUser, sid, 2);
    }

    /**
     * Verifies that a folder invitation email has been sent numEmails times.
     */
    private void verifyFolderInvitation(User sharer, User sharee, SID sid, int numEmails)
            throws Exception
    {
        verify(factEmailer, times(numEmails))
                .createFolderInvitationEmailer(eq(sharer), eq(sharee), eq(sid.toStringFormal()),
                        eq(""), any(SID.class), any(Role.class));
    }

    private void verifyNewUserAccountInvitation(User sharer, User sharee, SID sid,
            boolean shouldBeInvited)
            throws Exception
    {
        verify(factEmailer, shouldBeInvited ? times(1) : never())
                .createSignUpInvitationEmailer(eq(sharer), eq(sharee), eq(sid.toStringFormal()),
                        any(Role.class), eq(""), anyString());
    }
}
