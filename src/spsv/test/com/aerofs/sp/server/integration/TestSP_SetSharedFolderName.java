/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import java.util.List;

import static com.aerofs.proto.Sp.PBSharedFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TestSP_SetSharedFolderName extends AbstractSPFolderTest
{
    @Test
    public void shouldChangeFolderName() throws Exception
    {
        final String NEW_NAME = "aaa";

        // USER_1 shares SID_1 with USER_2
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));
        final String originalName = getSharedFolderName(SID_1, USER_1);

        // USER_1 renames the folder
        setSession(USER_1);
        service.setSharedFolderName(SID_1.toPB(), NEW_NAME);

        // Check that USER_1 has the new name
        assertEquals(NEW_NAME, getSharedFolderName(SID_1, USER_1));

        // Check that USER_2 still has the original name
        assertEquals(originalName, getSharedFolderName(SID_1, USER_2));
    }

    @Test(expected = ExBadArgs.class)
    public void shouldThrowIfEmptyName()
            throws Exception
    {
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.WRITE));

        setSession(USER_1);
        service.setSharedFolderName(SID_1.toPB(), "");
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfFolderNotFound()
            throws Exception
    {
        setSession(USER_1);
        service.setSharedFolderName(SID_1.toPB(), "aaaa");
    }

    /**
     * Tests that when a user accepts an invitation, he gets the same folder name as the inviter
     */
    @Test
    public void inviteeShouldGetInvitersFolderNameWhenJoining()
            throws Exception
    {
        final String NEW_NAME = "cats pics";

        // USER_1 shares a folder with USER_2
        shareAndJoinFolder(USER_1, SID_1, USER_2, Permissions.allOf(Permission.MANAGE));

        // USER_2 renames the folder
        setSession(USER_2);
        service.setSharedFolderName(SID_1.toPB(), NEW_NAME);

        // Check that USER_1 and USER_2 have different names for the folder
        assertNotEquals(getSharedFolderName(SID_1, USER_1), getSharedFolderName(SID_1, USER_2));

        // Now USER_2 shares with USER_3
        shareAndJoinFolder(USER_2, SID_1, USER_3, Permissions.allOf(Permission.WRITE));

        // Check that USER_3 has the new name, not the original folder name
        assertEquals(NEW_NAME, getSharedFolderName(SID_1, USER_3));
    }

    /**
     * Queries SP for the name of a given shared folder.
     * Side effect: changes the current session user.
     */
    private String getSharedFolderName(SID sid, User user)
            throws Exception
    {
        setSession(user);
        List<PBSharedFolder> folders = service.listUserSharedFolders(user.id().getString()).get()
                .getSharedFolderList();

        for (PBSharedFolder folder : folders) {
            if (new SID(folder.getStoreId()).equals(sid)) return folder.getName();
        }

        throw new ExNotFound();
    }
}
