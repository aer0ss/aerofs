/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestSP_UpdateACL extends AbstractSPACLTest
{
    @Test
    public void updateACL_shouldAllowToChangeExistingACLs()
            throws Exception
    {
        // add user 3 as editor for store # 1
        shareAndJoinFolder(USER_1, SID_1, USER_3, Permissions.EDITOR);

        clearVerkehrPublish(); // clear out notifications from sharing

        // update ACL for user 3 as user 1
        setSessionUser(USER_1);
        service.updateACL(SID_1.toPB(), USER_3.id().getString(),
                Permissions.OWNER.toPB(), false);

        // check that notifications were published on update
        assertVerkehrPublishOnlyContains(USER_1, USER_3);

        // verify user 3 has updated ACL in place
        setSessionUser(USER_3);
        GetACLReply reply = service.getACL(0L).get();

        // epoch for this guy should be 2 (started at 0, added as editor then as owner)
        assertGetACLReplyIncrementsEpochBy(reply, 2);
        assertACLOnlyContains(getSingleACL(SID_1, reply),
                new UserAndRole(USER_1, Permissions.OWNER),
                new UserAndRole(USER_3, Permissions.OWNER));
    }
    
    @Test
    public void updateACL_shouldAllowToChangePendingACLs()
            throws Exception
    {
        // add user 3 as PENDING editor for store # 1
        shareFolder(USER_1, SID_1, USER_3, Permissions.EDITOR);
        clearVerkehrPublish(); // clear out notifications from sharing

        // update ACL for user 3 as user 1
        setSessionUser(USER_1);
        service.updateACL(SID_1.toPB(), USER_3.id().getString(),
                Permissions.OWNER.toPB(), false);

        sqlTrans.begin();
        assertEquals(Permissions.OWNER,
                factSharedFolder.create(SID_1) .getPermissionsNullable(USER_3));
        sqlTrans.commit();

        // check that no notifications were published on update
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void updateACL_shouldAllowToChangeLeftACLs()
            throws Exception
    {
        // add user 3 as LEFT editor for store # 1
        shareAndJoinFolder(USER_1, SID_1, USER_3, Permissions.EDITOR);
        leaveSharedFolder(USER_3, SID_1);
        clearVerkehrPublish(); // clear out notifications from sharing

        // update ACL for user 3 as user 1
        setSessionUser(USER_1);
        service.updateACL(SID_1.toPB(), USER_3.id().getString(),
                Permissions.OWNER.toPB(), false);

        sqlTrans.begin();
        assertEquals(Permissions.OWNER,
                factSharedFolder.create(SID_1) .getPermissionsNullable(USER_3));
        sqlTrans.commit();

        // check that no notifications were published on update
        assertVerkehrPublishIsEmpty();
    }

    @Test
    public void updateACL_shouldThrowOnUpdatingNonexistingACLs()
            throws Exception
    {
        // add the owner for store # 1
        shareFolder(USER_1, SID_1, newUser(), Permissions.OWNER);
        clearVerkehrPublish(); // throw away this notification

        // update ACL for user 3 as user 1
        setSessionUser(USER_1);
        try {
            // should fail with ExNotFound
            service.updateACL(SID_1.toPB(), USER_3.id().getString(),
                    Permissions.OWNER.toPB(), false);
            // must not reach here
            fail();
        } catch (Exception e) {
            // make sure we clean up after uncommitted transaction(s)
            sqlTrans.handleException();
        }

        assertVerkehrPublishIsEmpty();

        // check that user 3 still has no ACLs set in the db
        setSessionUser(USER_3);
        GetACLReply reply = service.getACL(0L).get();
        assertGetACLReplyIncrementsEpochBy(reply, 0);
        assertEquals(0, reply.getStoreAclCount());
    }

    @Test
    public void updateACL_shouldForbidNonOwnerToUpdateACLs()
            throws Exception
    {
        // add user 3 as editor for store # 1
        shareAndJoinFolder(USER_1, SID_1, USER_3, Permissions.EDITOR);
        clearVerkehrPublish(); // throw away these notifications

        // try to edit user 1's ACL entry for store 1 as user 3
        setSessionUser(USER_3);
        try {
            service.updateACL(SID_1.toPB(), USER_1.id().getString(),
                    Permissions.EDITOR.toPB(), false);
            fail();
        } catch (ExNoPerm e) {
            // make sure we clean up after uncommitted transaction(s)
            sqlTrans.handleException();
        }

        assertVerkehrPublishIsEmpty();

        // check that user 3 only has editor permissions
        setSessionUser(USER_3);
        GetACLReply reply = service.getACL(0L).get();

        assertGetACLReplyIncrementsEpochBy(reply, 1);
        assertACLOnlyContains(getSingleACL(SID_1, reply),
                new UserAndRole(USER_1, Permissions.OWNER),
                new UserAndRole(USER_3, Permissions.EDITOR));
    }

    @Test
    public void updateACL_shouldAllowTeamAdminOfOwnerToUpdateACL()
            throws Exception
    {
        // add USER_3 as owner of the store
        shareAndJoinFolder(USER_1, SID_1, USER_3, Permissions.OWNER);
        clearVerkehrPublish();

        User admin = addAdmin(USER_3);

        // try to edit user 1's ACL entry for store 1 as user 3
        setSessionUser(admin);
        service.updateACL(SID_1.toPB(), USER_1.id().getString(),
                Permissions.EDITOR.toPB(), false);

        // switch to USER_3 so we can verify epoch number increments below.
        setSessionUser(USER_3);
        GetACLReply reply = service.getACL(0L).get();

        assertGetACLReplyIncrementsEpochBy(reply, 2);
        assertACLOnlyContains(getSingleACL(SID_1, reply),
                new UserAndRole(USER_1, Permissions.EDITOR),
                new UserAndRole(USER_3, Permissions.OWNER));
    }

    @Test
    public void updateACL_shouldAllowTeamAdminOfPendingOwnerToUpdateACL()
            throws Exception
    {
        // add USER_3 as owner of the store
        shareFolder(USER_1, SID_1, USER_3, Permissions.OWNER);
        clearVerkehrPublish();

        User admin = addAdmin(USER_3);

        // try to edit user 1's ACL entry for store 1 as user 3
        setSessionUser(admin);
        service.updateACL(SID_1.toPB(), USER_1.id().getString(),
                Permissions.EDITOR.toPB(), false);

        setSessionUser(USER_1);
        GetACLReply reply = service.getACL(0L).get();

        assertACLOnlyContains(getSingleACL(SID_1, reply),
                new UserAndRole(USER_1, Permissions.EDITOR));
    }

    @Test
    public void updateACL_shouldAllowTeamAdminOfLeftOwnerToUpdateACL()
            throws Exception
    {
        // add USER_3 as owner of the store
        shareAndJoinFolder(USER_1, SID_1, USER_3, Permissions.OWNER);
        leaveSharedFolder(USER_3, SID_1);
        clearVerkehrPublish();

        User admin = addAdmin(USER_3);

        // try to edit user 1's ACL entry for store 1 as user 3
        setSessionUser(admin);
        service.updateACL(SID_1.toPB(), USER_1.id().getString(),
                Permissions.EDITOR.toPB(), false);

        setSessionUser(USER_1);
        GetACLReply reply = service.getACL(0L).get();

        assertACLOnlyContains(getSingleACL(SID_1, reply),
                new UserAndRole(USER_1, Permissions.EDITOR));
    }

    @Test
    public void updateACL_shouldSendNotificationEmail()
            throws Exception
    {
        // add USER_3 as owner of the store
        shareAndJoinFolder(USER_1, SID_1, USER_3, Permissions.OWNER);

        setSessionUser(USER_3);
        service.updateACL(SID_1.toPB(), USER_1.id().getString(),
                Permissions.EDITOR.toPB(), false);

        // the second call to updateACL with the same role should send no notification email
        service.updateACL(SID_1.toPB(), USER_1.id().getString(),
                Permissions.EDITOR.toPB(), false);

        verify(sharedFolderNotificationEmailer, times(1)).sendRoleChangedNotificationEmail(
                factSharedFolder.create(SID_1), USER_3, USER_1,
                Permissions.OWNER,
                Permissions.EDITOR);
    }
}
