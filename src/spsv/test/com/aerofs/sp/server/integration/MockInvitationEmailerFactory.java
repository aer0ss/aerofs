/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.SID;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MockInvitationEmailerFactory extends InvitationEmailer.Factory
{
    @Override
    public InvitationEmailer createSignUpInvitationEmailer(@Nullable final String inviter,
            final String invitee, final String inviterName, @Nullable final String folderName,
            @Nullable final String note, final String signupCode)
    {
        return createNullEmailer();
    }

    @Override
    public InvitationEmailer createFolderInvitationEmailer(@Nonnull final String from,
            final String to, final String fromPerson, @Nullable final String folderName,
            @Nullable final String note, final SID sid)
    {
        return createNullEmailer();
    }

    @Override
    public InvitationEmailer createOrganizationInvitationEmailer(@Nonnull final User inviter,
            @Nonnull final User invitee, @Nonnull final Organization organization)
    {
        return createNullEmailer();
    }
}
