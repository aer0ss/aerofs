/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.members;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.sharing.members.SharedFolderMember.Factory;
import com.aerofs.gui.sharing.members.SharedFolderMember.User;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import javax.annotation.Nonnull;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class TestSharedFolderMember extends AbstractTest
{
    @Mock CfgLocalUser _cfgLocalUser;
    @InjectMocks Factory _factory;

    UserID _localUserID = UserID.fromInternal("test@example.com");

    @Before
    public void setup()
    {
        when(_cfgLocalUser.get()).thenReturn(_localUserID);
    }

    @Test
    public void getLabel_shouldReturnMeForLocalUser()
    {
        assertGetLabelResult("me", "FirstName", "LastName", _localUserID.getString());
    }

    @Test
    public void getLabel_shouldReturnLabelForOtherUsers()
    {
        assertGetLabelResult("FirstName LastName", "FirstName", "LastName", "email");
        assertGetLabelResult("OnlyFirstName", "OnlyFirstName", "", "email");
        assertGetLabelResult("OnlyLastName", "", "OnlyLastName", "email");
        assertGetLabelResult("email", "", "", "email");
    }

    @Test
    public void getLabel_shouldTrimWhiteSpaceInAllCases()
    {
        assertGetLabelResult("FirstName LastName", "   FirstName  ", "  LastName   ", "email");
        assertGetLabelResult("OnlyFirstName", "   OnlyFirstName   ", "", "email");
        assertGetLabelResult("OnlyLastName", "    ", "   OnlyLastName   ", "email");
        assertGetLabelResult("email", "   ", "    ", "email");
        assertGetLabelResult("Ludwig von Beethoven I", "  Ludwig von  ", "  Beethoven I  ", "email");
    }

    private void assertGetLabelResult(String expected, @Nonnull String firstName,
            @Nonnull String lastName, @Nonnull String email)
    {
        SharedFolderMember member = new User(_factory, UserID.fromInternal(email),
                firstName, lastName, Permissions.EDITOR, SharedFolderState.JOINED);

        assertEquals(expected, member.getLabel());
    }
}
