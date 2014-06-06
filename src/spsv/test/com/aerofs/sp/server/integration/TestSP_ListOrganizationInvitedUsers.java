/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class TestSP_ListOrganizationInvitedUsers extends AbstractSPFolderTest
{
    @Test
    public void shouldHideExternallyManagedUsers()
            throws Exception
    {
        sqlTrans.begin();
        User admin = saveUser();
        admin.setLevel(AuthorizationLevel.ADMIN);
        sqlTrans.commit();

        setSession(admin);
        service.inviteToOrganization(newUser().id().getString());
        service.inviteToOrganization(newUser().id().getString());
        service.inviteToOrganization(newUser().id().getString());

        assertEquals(service.listOrganizationInvitedUsers().get().getUserIdCount(), 3);

        when(authenticator.isLocallyManaged(any(UserID.class))).thenReturn(false);

        assertEquals(service.listOrganizationInvitedUsers().get().getUserIdCount(), 0);
    }
}
