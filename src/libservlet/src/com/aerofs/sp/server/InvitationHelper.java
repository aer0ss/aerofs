/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Lists;
import com.unboundid.ldap.sdk.LDAPSearchException;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

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

    public List<InvitationEmailer> createFolderInvitationAndEmailer(SharedFolder sf, User sharer,
            Set<User> needEmails, Permissions permissions, @Nullable String note, String folderName)
            throws
            SQLException,
            IOException,
            ExNotFound,
            ExAlreadyExist,
            ExExternalServiceUnavailable,
            LDAPSearchException
    {
        List<InvitationEmailer> result = Lists.newLinkedList();
        for (User sharee : needEmails) {
            result.add(_factInvitationEmailer.createFolderInvitationEmailer(sharer, sharee,
                folderName, note, sf.id(), permissions));
        }
        return result;
    }

    public InvitationEmailer createFolderInvitationAndEmailer(SharedFolder sf, User sharer,
            User sharee, Permissions permissions, @Nullable String note, String folderName)
            throws
            SQLException,
            IOException,
            ExNotFound,
            ExAlreadyExist,
            ExExternalServiceUnavailable,
            LDAPSearchException
    {
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

    public InvitationEmailer createBatchFolderInvitationAndEmailer(Group sharer,
            User newMember, Set<SharedFolder> needsEmail)
            throws
            IOException,
            SQLException,
            ExNotFound
    {
        if (needsEmail.size() == 0) {
            return _factInvitationEmailer.createAddedToGroupEmailer(newMember, sharer);
        } else {
            return _factInvitationEmailer.createBatchInvitationEmailer(newMember, sharer, needsEmail);
        }
    }

    public InvitationEmailer doesNothing()
    {
        return _factInvitationEmailer.doesNothing();
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
            _esdb.insertEmailSubscription(invitee.id(),
                    SubscriptionCategory.AEROFS_INVITATION_REMINDER);
        } else {
            // No signup code is needed for auto-provisioned users.
            // They can simply sign in using their externally-managed account credentials.
            code = null;

            // We can't set up reminder emails as we do for locally-managed users, because
            // reminder email implementation requires valid signup codes. We can implement
            // different reminder emails if we'd like. In doing that, we need to remove the
            // reminder when creating the user during auto-provisioning.
        }

        InvitationEmailer emailer = _factInvitationEmailer.createSignUpInvitationEmailer(inviter,
                invitee, folderName, permissions, note, code);

        return new InviteToSignUpResult(emailer, code);
    }
}
