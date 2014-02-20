/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.unboundid.ldap.sdk.LDAPSearchException;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;

public class InvitationHelper
{
    private final Authenticator _authenticator;
    private final InvitationEmailer.Factory _factInvitationEmailer;
    private final EmailSubscriptionDatabase _esdb;

    @Inject
    public InvitationHelper(Authenticator authenticator,
            InvitationEmailer.Factory factInvitationEmailer, EmailSubscriptionDatabase esdb)
    {
        _authenticator = authenticator;
        _factInvitationEmailer = factInvitationEmailer;
        _esdb = esdb;
    }

    public InvitationEmailer createFolderInvitationAndEmailer(SharedFolder sf, User sharer,
            User sharee, Permissions permissions, @Nullable String note, String folderName)
            throws SQLException, IOException, ExNotFound, ExAlreadyExist,
            ExExternalServiceUnavailable, LDAPSearchException
    {
        SharedFolderState state = sf.getStateNullable(sharee);
        if (state == SharedFolderState.JOINED) {
            // TODO (WW) throw ExAlreadyJoined?
            throw new ExAlreadyExist(sharee.id() + " is already joined");
        } else if (state != null) {
            // Set user as pending if the user exists but in a non-joined state
            sf.setState(sharee, SharedFolderState.PENDING);
        } else {
            // Add a pending ACL entry if the user doesn't exist
            sf.addPendingUser(sharee, permissions, sharer);
        }

        InvitationEmailer emailer;
        if (sharee.exists()) {
            // send folder invitation email
            emailer = _factInvitationEmailer.createFolderInvitationEmailer(sharer, sharee,
                    folderName, note, sf.id(), permissions);
        } else {
            // send sign-up email
            emailer = inviteToSignUp(sharer, sharee, folderName, permissions, note)._emailer;
        }
        return emailer;
    }

    public static class InviteToSignUpResult
    {
        final InvitationEmailer _emailer;
        @Nullable final String _signUpCode;

        InviteToSignUpResult(InvitationEmailer emailer, @Nullable String signUpCode)
        {
            _emailer = emailer;
            _signUpCode = signUpCode;
        }
    }

    /**
     * Call this method to use an inviter name different from inviter.getFullName()._first
     */
    public InviteToSignUpResult inviteToSignUp(User inviter, User invitee,
            @Nullable String folderName, @Nullable Permissions permissions, @Nullable String note)
            throws SQLException, IOException, ExNotFound, ExExternalServiceUnavailable,
            LDAPSearchException
    {
        assert !invitee.exists();

        String code;
        if (_authenticator.isLocallyManaged(invitee.id())) {
            code = invitee.addSignUpCode();
            _esdb.insertEmailSubscription(invitee.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);
        } else {
            // No signup code is needed for auto-provisioned users.
            // They can simply sign in using their externally-managed account credentials.
            code = null;

            // We can't set up reminder emails as we do for locall-managed users, because
            // reminder email implementation requires valid signup codes. We can implement
            // different reminder emails if we'd like. In doing that, we need to remove the
            // reminder when creating the user during auto-provisioning.
        }

        InvitationEmailer emailer = _factInvitationEmailer.createSignUpInvitationEmailer(inviter,
                invitee, folderName, permissions, note, code);

        return new InviteToSignUpResult(emailer, code);
    }
}
