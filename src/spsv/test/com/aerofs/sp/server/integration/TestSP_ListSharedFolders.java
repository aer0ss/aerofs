/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.ids.SID;
import com.aerofs.proto.Sp.ListSharedFoldersReply;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestSP_ListSharedFolders extends AbstractSPFolderTest
{
    @Test
    public void shouldListRegardlessOfStateAndRoleOfSessionUser() throws Exception
    {
        sqlTrans.begin();
        User sharer = saveUser();
        User sessionUser = saveUser();
        sqlTrans.commit();

        for (Permission permission : Permission.values()) {
            Permissions permissions = Permissions.allOf(permission);
            for (SharedFolderState state : SharedFolderState.values()) {
                SID sid = SID.generate();

                if (state == SharedFolderState.PENDING) {
                    shareFolder(sharer, sid, sessionUser, permissions);
                } else if (state == SharedFolderState.JOINED) {
                    shareAndJoinFolder(sharer, sid, sessionUser, permissions);
                } else if (state == SharedFolderState.LEFT) {
                    shareAndJoinFolder(sharer, sid, sessionUser, permissions);
                    leaveSharedFolder(sessionUser, sid);
                } else {
                    fail();
                }

                setSession(sessionUser);
                ListSharedFoldersReply reply =
                        service.listSharedFolders(ImmutableList.of(BaseUtil.toPB(sid))).get();

                assertEquals(1, reply.getSharedFolderCount());
            }
        }
    }

    @Test
    public void shouldListForTeamServerIfAnOrganizationMemberHasJoined() throws Exception
    {
        sqlTrans.begin();
        User sharer = saveUser();
        User sharee = saveUser();
        User ts = sharee.getOrganization().getTeamServerUser();
        sqlTrans.commit();

        SID sid = SID.generate();
        shareAndJoinFolder(sharer, sid, sharee, Permissions.allOf(Permission.WRITE));

        setSession(ts);
        ListSharedFoldersReply reply =
                service.listSharedFolders(ImmutableList.of(BaseUtil.toPB(sid))).get();

        assertEquals(1, reply.getSharedFolderCount());
    }

    @Test
    public void shouldThrowIfSessionUserIsNotAMemberOfOneStore() throws Exception
    {
        sqlTrans.begin();
        User sharer = saveUser();
        User sessionUser = saveUser();
        User otherUser = saveUser();
        sqlTrans.commit();

        // two stores because we are covering the logic of at least one store with no permission,
        // not all stores.
        SID sid1 = SID.generate();
        shareFolder(sharer, sid1, sessionUser, Permissions.allOf(Permission.WRITE));

        SID sid2 = SID.generate();
        shareFolder(sharer, sid2, otherUser, Permissions.allOf(Permission.WRITE));

        setSession(sessionUser);

        try {
            service.listSharedFolders(ImmutableList.of(BaseUtil.toPB(sid1), BaseUtil.toPB(sid2)));
            fail();
        } catch (ExNoPerm e) {
            // expected
            sqlTrans.handleException();
        }
    }

    @Test
    public void shouldThrowForTeamServerIfNoOrganizationMembersHaveJoined() throws Exception
    {
        sqlTrans.begin();
        User sharer = saveUser(); // org 1
        User pendingSharee = saveUser(); // org 2
        User joinedSharee = saveUser(); // org 2
        User leftSharee = saveUser(); // org 2
        User otherSharee = saveUser(); // org 3

        Organization org = pendingSharee.getOrganization();
        User ts = org.getTeamServerUser(); // ts of org 2

        joinedSharee.setOrganization(org, AuthorizationLevel.USER);
        leftSharee.setOrganization(org, AuthorizationLevel.USER);
        sqlTrans.commit();

        // two stores because we are covering the logic of at least one store with no permission,
        // not all stores.
        SID sid = SID.generate();
        shareAndJoinFolder(sharer, sid, joinedSharee, Permissions.allOf(Permission.WRITE));

        SID sid2 = SID.generate();
        shareAndJoinFolder(sharer, sid2, otherSharee, Permissions.allOf(Permission.WRITE));
        shareFolder(sharer, sid2, pendingSharee, Permissions.allOf(Permission.WRITE));
        shareAndJoinFolder(sharer, sid2, leftSharee, Permissions.allOf(Permission.WRITE));
        leaveSharedFolder(leftSharee, sid2);

        setSession(ts);

        try {
            service.listSharedFolders(ImmutableList.of(BaseUtil.toPB(sid), BaseUtil.toPB(sid2)));
            fail();
        } catch (ExNoPerm e) {
            // expected
            sqlTrans.handleException();
        }
    }
}
