/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class TestSP_GetACL extends AbstractSPACLTest
{
    @Test
    public void getACL_shouldAllowAnyUserWithAnyRoleToGetACL()
            throws Exception
    {
        // share store # 1
        shareAndJoinFolder(USER_1, SID_1, USER_3, Permissions.allOf(Permission.WRITE));

        // share store # 2
        shareAndJoinFolder(USER_2, SID_2, USER_3, Permissions.allOf(Permission.WRITE));

        // now have the editor do a getacl call

        setSession(USER_3);
        GetACLReply reply = service.getACL(0L).get();

        // epoch for this guy should be 2 (started at 0, added twice as editor)
        assertGetACLReplyIncrementsEpochBy(reply, 2);

        // he should have 2 store entries (since he's the editor of 2 stores)
        assertEquals(2, reply.getStoreAclCount());

        for (PBStoreACL storeACL : reply.getStoreAclList()) {
            SID aclSID = new SID(storeACL.getStoreId());
            if (aclSID.equals(SID_1)) {
                assertACLOnlyContains(storeACL.getSubjectPermissionsList(),
                        new UserAndRole(USER_1, Permissions.allOf(Permission.WRITE,
                                Permission.MANAGE)),
                        new UserAndRole(USER_3, Permissions.allOf(Permission.WRITE)));
            } else if (aclSID.equals(SID_2)) {
                assertACLOnlyContains(storeACL.getSubjectPermissionsList(),
                        new UserAndRole(USER_2, Permissions.allOf(Permission.WRITE,
                                Permission.MANAGE)),
                        new UserAndRole(USER_3, Permissions.allOf(Permission.WRITE)));
            } else {
                fail("unexpected store acl for s:" + aclSID);
            }
        }
    }

    @Test
    public void getACL_shouldNotIncludePendingMembers() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        shareFolder(USER_1, SID_1, USER_3, Permissions.allOf(Permission.WRITE, Permission.MANAGE));

        checkACL(USER_1, Permissions.allOf(Permission.WRITE, Permission.MANAGE));

        joinSharedFolder(USER_2, SID_1);

        checkACL(new UserAndRole(USER_1, Permissions.allOf(Permission.WRITE, Permission.MANAGE)),
                new UserAndRole(USER_2, Permissions.allOf(Permission.WRITE)));

        joinSharedFolder(USER_3, SID_1);

        checkACL(new UserAndRole(USER_1, Permissions.allOf(Permission.WRITE, Permission.MANAGE)),
                new UserAndRole(USER_2, Permissions.allOf(Permission.WRITE)),
                new UserAndRole(USER_3, Permissions.allOf(Permission.WRITE, Permission.MANAGE)));

        leaveSharedFolder(USER_2, SID_1);

        checkACL(new UserAndRole(USER_1, Permissions.allOf(Permission.WRITE, Permission.MANAGE)),
                new UserAndRole(USER_3, Permissions.allOf(Permission.WRITE, Permission.MANAGE)));

        leaveSharedFolder(USER_1, SID_1);

        // USER_1 is the session user, hence the empty ACL reply
        assertEquals(service.getACL(0L).get().getStoreAclCount(), 0);

        setSession(USER_3);
        checkACL(USER_3, Permissions.allOf(Permission.WRITE, Permission.MANAGE));
    }

    @Test
    public void shouldReturnExternalFlagFalseAfterSharing() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        assertFalse(isExternal(USER_1, SID_1));
    }

    @Test
    public void shouldReturnExternalFlagTrueAfterSharingExternal() throws Exception
    {
        shareFolderExternal(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        assertTrue(isExternal(USER_1, SID_1));
    }

    @Test
    public void shouldReturnExternalFlagFalseAfterJoining() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        joinSharedFolder(USER_2, SID_1);

        assertFalse(isExternal(USER_2, SID_1));
    }

    @Test
    public void shouldReturnExternalFlagFalseAfterJoiningRegardlessOfOwnerFlag() throws Exception
    {
        shareFolderExternal(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        joinSharedFolder(USER_2, SID_1);

        assertFalse(isExternal(USER_2, SID_1));
    }

    @Test
    public void shouldReturnExternalFlagTrueAfterJoiningExternal() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        joinSharedFolderExternal(USER_2, SID_1);

        assertTrue(isExternal(USER_2, SID_1));
    }

    @Test
    public void shouldReturnExternalFlagTrueAfterJoiningExternalRegardlessOfOwnerFlag() throws Exception
    {
        shareFolderExternal(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        joinSharedFolderExternal(USER_2, SID_1);

        assertTrue(isExternal(USER_2, SID_1));
    }

    @Test
    public void shouldNotAlterExternalFlagAfterFirstShareExternal() throws Exception
    {
        shareFolderExternal(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        shareFolder(USER_1, SID_1, USER_3, Permissions.allOf(Permission.WRITE));

        assertTrue(isExternal(USER_1, SID_1));
    }

    @Test
    public void shouldNotAlterExternalFlagAfterFirstShare() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        shareFolderExternal(USER_1, SID_1, USER_3, Permissions.allOf(Permission.WRITE));

        assertFalse(isExternal(USER_1, SID_1));
    }

    @Test
    public void shouldAllowJoinAfterLeaveExternal() throws Exception
    {
        shareFolderExternal(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        leaveSharedFolder(USER_1, SID_1);
        joinSharedFolder(USER_1, SID_1);

        assertFalse(isExternal(USER_1, SID_1));
    }

    @Test
    public void shouldAllowJoinExternalAfterLeave() throws Exception
    {
        shareFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        leaveSharedFolder(USER_1, SID_1);
        joinSharedFolderExternal(USER_1, SID_1);

        assertTrue(isExternal(USER_1, SID_1));
    }

    private boolean isExternal(User user, SID sid) throws Exception
    {
        setSession(user);
        GetACLReply reply = service.getACL(0L).get();
        for (PBStoreACL sacl : reply.getStoreAclList()) {
            if (new SID(sacl.getStoreId()).equals(sid)) return sacl.getExternal();
        }
        throw new IllegalStateException();
    }

    private void checkACL(User user, Permissions permissions) throws Exception
    {
        checkACL(new UserAndRole(user, permissions));
    }

    private void checkACL(UserAndRole ... urs) throws Exception
    {
        assertACLOnlyContains(getSingleACL(SID_1, service.getACL(0L).get()), urs);
    }
}
