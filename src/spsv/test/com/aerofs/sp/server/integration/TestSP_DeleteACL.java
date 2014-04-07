/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.proto.Sp.GetACLReply;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestSP_DeleteACL extends AbstractSPACLTest
{

    @Test
    public void deleteACL_shouldAllowOwnerToDeleteAndNotifyAllAffectedUsers()
            throws Exception
    {
        // share a folder and add a second person (as owner)
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
        clearPublishedMessages();

        // add a third person (as editor)
        shareAndJoinFolder(USER_1, SID_1, USER_3, Permissions.allOf(Permission.WRITE));
        clearPublishedMessages();

        // now have the second guy delete the third

        setSession(USER_2);
        service.deleteACL(SID_1.toPB(), USER_3.id().getString()).get();

        // expect first, second and third guy all to be notified

        assertVerkehrPublishedOnlyTo(USER_1, USER_2, USER_3);

        // have the first guy get his acl

        setSession(USER_1);
        GetACLReply reply = service.getACL(0L).get();

        // this guy has seen _all_ the updates, so he should see an epoch of 4
        assertGetACLReplyIncrementsEpochBy(reply, 4);

        assertACLOnlyContains(getSingleACL(SID_1, reply),
                new UserAndRole(USER_1, Permissions.allOf(Permission.WRITE, Permission.MANAGE)),
                new UserAndRole(USER_2, Permissions.allOf(Permission.WRITE, Permission.MANAGE)));

        // now have the deleted guy get his acl

        setSession(USER_3);
        reply = service.getACL(0L).get();

        // only two updates have affected him, so he should have an epoch of 2
        assertGetACLReplyIncrementsEpochBy(reply, 2);
        // since all entries have been deleted for this user, there should be no entries for him
        assertEquals(0, reply.getStoreAclCount());
    }

    @Test
    public void deleteACL_shouldReturnSuccessfullyEvenIfACLDoesntContainSubject()
            throws Exception
    {
        // share folder
        shareFolder(USER_1, SID_1, newUser(), Permissions.allOf(Permission.WRITE));
        clearPublishedMessages(); // don't care

        // now attempt to delete someone for whom the role doesn't exist

        setSession(USER_1);

        try {
            service.deleteACL(SID_1.toPB(), USER_2.id().getString()).get();
            // must not reach here
            fail();
        } catch (ExNotFound e) {
            sqlTrans.handleException();
        }

        setSession(USER_1);
        GetACLReply reply = service.getACL(0L).get();

        // epoch shouldn't be bumped on a deletion of a person that doesn't exist
        assertGetACLReplyIncrementsEpochBy(reply, 1);

        assertACLOnlyContains(getSingleACL(SID_1, reply), USER_1, Permissions.allOf(
                Permission.WRITE, Permission.MANAGE));
    }

    @Test
    public void deleteACL_shouldForbitNonOwnerToDeleteACLs()
            throws Exception
    {
        // share folder with an editor
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        // get the editor to try to delete the owner
        setSession(USER_2);

        try {
            service.deleteACL(SID_1.toPB(), USER_1.id().getString()).get();
            // must not reach here
            fail();
        } catch (ExNoPerm e) {}
    }
}
