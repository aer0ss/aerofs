/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Assert;
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
    @Test
    public void shouldNotThrowWhenInviteeListEmptyForInternalFolders()
            throws Exception
    {
        setSession(USER_1);
        service.shareFolder(SID_1.toStringFormal(), SID_1.toPB(),
                Collections.<PBSubjectPermissions>emptyList(), "", false, false).get();
    }

    @Test
    public void shouldNotThrowWhenInviteeListEmptyForExternalFolders()
            throws Exception
    {
        setSession(USER_1);
        service.shareFolder(SID_1.toStringFormal(), SID_1.toPB(),
                Collections.<PBSubjectPermissions>emptyList(), "", true, false).get();
    }

    @Test
    public void shouldSuccessfullyShareFolderWithOneUser()
            throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        assertVerkehrPublishedOnlyTo(USER_1);
        verifyFolderInvitation(USER_1, USER_2, SID_1, 1);
        verifyNewUserAccountInvitation(USER_1, USER_2, SID_1, false);
    }

    @Test
    public void shouldSuccessfullyShareFolderExternalWithOneUser()
            throws Exception
    {
        shareFolderExternal(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        assertVerkehrPublishedOnlyTo(USER_1);
        verifyFolderInvitation(USER_1, USER_2, SID_1, 1);
        verifyNewUserAccountInvitation(USER_1, USER_2, SID_1, false);
    }

    @Test
    public void shouldInviteNonExistingUser()
            throws Exception
    {
        User user = newUser();
        // the new hasn't actually been added to the db yet so this should trigger an invite to them
        shareFolder(USER_1, SID_1, user, Permissions.allOf(Permission.WRITE));

        assertVerkehrPublishedOnlyTo(USER_1);
        verifyFolderInvitation(USER_1, user, SID_1, 0);
        verifyNewUserAccountInvitation(USER_1, user, SID_1, true);
    }

    @Test
    public void shouldThrowWhenEditorTriesToInviteToFolder()
            throws Exception
    {
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        clearPublishedMessages();

        // Set USER_2 as a non-admin
        sqlTrans.begin();
        USER_2.setOrganization(USER_1.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();

        try {
            // should throw ExNoPerm because user 2 is an editor
            shareFolder(USER_2, SID_1, USER_3, Permissions.allOf(Permission.WRITE));
            fail();
        } catch (ExNoPerm e) {
            sqlTrans.handleException();
        }
        assertNothingPublished();
    }

    @Test
    public void shouldThrowExBadArgsWhenTryingToShareRootStore() throws Exception
    {
        try {
            shareFolder(USER_1, SID.rootSID(USER_1.id()), USER_2, Permissions.allOf(
                    Permission.WRITE));
            fail();
        } catch (ExBadArgs e) {
            sqlTrans.handleException();
        }
        assertNothingPublished();
    }

    @Test
    public void shouldThrowExAlreadyExistWhenInvitingExistingMember()
            throws Exception
    {
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        clearPublishedMessages();

        try {
            shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
            fail();
        } catch (ExAlreadyExist e) {
            sqlTrans.handleException();
        }
        assertNothingPublished();
    }

    @Test
    public void shouldAllowInvitingExistingPendingUsers()
            throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        // the second call should not fail
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
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

        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        // 3 refresh CRL commands.
        List<Command> commands = Lists.newArrayListWithExpectedSize(3);

        for (Published published : getPublishedMessages()) {
            try {
                Command command = Command.parseFrom(published.bytes);
                Assert.assertEquals(CommandType.REFRESH_CRL, command.getType());
                commands.add(command);
            } catch (InvalidProtocolBufferException e) {
                // noop - lots of other stuff is published
            }
        }

        Assert.assertEquals(3, commands.size());
    }

    @Test
    public void shouldAllowToShareIfNoACLExists()
            throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE, Permission.MANAGE));

        GetACLReply reply = service.getACL(0L).get();

        assertGetACLReplyIncrementsEpochBy(reply, 1);
        assertACLOnlyContains(getSingleACL(SID_1, reply), USER_1, Permissions.allOf(
                Permission.WRITE, Permission.MANAGE));
        assertVerkehrPublishedOnlyTo(USER_1);
    }

    @Test
    public void shouldAllowOwnerToShareAndNotifyAllAffectedUsers()
            throws Exception
    {
        // create shared folder and invite a first user
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE, Permission.MANAGE));
        assertVerkehrPublishedOnlyTo(USER_1);
        clearPublishedMessages();

        // inviteee joins
        joinSharedFolder(USER_2, SID_1);
        assertVerkehrPublishedOnlyTo(USER_1, USER_2);
        clearPublishedMessages();

        // now lets see if the other person can add a third person
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        shareAndJoinFolder(USER_2, SID_1, user, Permissions.allOf(Permission.WRITE));
        assertVerkehrPublishedOnlyTo(USER_1, USER_2, user);
        clearPublishedMessages();

        // now let's see what the acls are like
        setSession(USER_1);
        GetACLReply reply = service.getACL(0L).get();

        assertGetACLReplyIncrementsEpochBy(reply, 3);

        assertACLOnlyContains(getSingleACL(SID_1, reply),
                new UserAndRole(USER_1, Permissions.allOf(Permission.WRITE, Permission.MANAGE)),
                new UserAndRole(USER_2, Permissions.allOf(Permission.WRITE, Permission.MANAGE)),
                new UserAndRole(user, Permissions.allOf(Permission.WRITE)));
    }

    @Test
    public void shouldForbidNonOwnerToShare()
            throws Exception
    {
        // share folder and invite a new editor
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        sqlTrans.begin();
        // set USER_2 as non-admin
        USER_2.setOrganization(USER_1.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();

        try {
            // get the editor to try and make some role changes
            shareAndJoinFolder(USER_2, SID_1, newUser(), Permissions.allOf(Permission.WRITE));
            // must not reach here
            org.junit.Assert.fail();
        } catch (ExNoPerm ignored) {}
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

        shareAndJoinFolder(owner, sid, leftUser, Permissions.allOf(Permission.WRITE));
        verifyFolderInvitation(owner, leftUser, sid, 1);

        leaveSharedFolder(leftUser, sid);
        shareFolder(owner, sid, leftUser, Permissions.allOf());

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
                        eq(""), any(SID.class), any(Permissions.class));
    }

    private void verifyNewUserAccountInvitation(User sharer, User sharee, SID sid,
            boolean shouldBeInvited)
            throws Exception
    {
        verify(factEmailer, shouldBeInvited ? times(1) : never())
                .createSignUpInvitationEmailer(eq(sharer), eq(sharee), eq(sid.toStringFormal()),
                        any(Permissions.class), eq(""), anyString());
    }
}
