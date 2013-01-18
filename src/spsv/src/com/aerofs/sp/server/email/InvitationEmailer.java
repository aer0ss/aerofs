/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.id.SID;
import com.aerofs.lib.FullName;
import com.aerofs.labeling.L;
import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.BaseParam.SV;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExEmailSendingFailed;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sv.client.SVClient;
import com.aerofs.sv.common.EmailCategory;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.user.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.Callable;

// TODO (WW) the pattern of this class is inconsistent with PasswordResetEmailer. Need refactoring
// and/or merging.
public class InvitationEmailer
{
    private final static String ACCEPT_INVITATION_LINK = SP.ADMIN_PANEL_BASE + "/accept";

    static String getSignUpLink(String signUpCode)
    {
        // Redirect the user to the install page right after signing up
        // N.B. the parameter key strings must be identical to the key strings in signup/views.py.
        return SP.ADMIN_PANEL_BASE + "/signup?c=" + signUpCode + "&next=" +
                Util.urlEncode("/install");
    }

    public static class Factory
    {
        public InvitationEmailer createNullEmailer()
        {
            return new InvitationEmailer(new Callable<Void>()
            {
                @Override
                public Void call()
                {
                    return null;
                }
            });
        }

        /**
         * @param inviter null if the invite is sent from the AeroFS team.
         *
         * TODO (WW) use a separate method rather than a null inviter for AeroFS initiated invites.
         */
        public InvitationEmailer createSignUpInvitationEmailer(@Nullable final String inviter,
                final String invitee, final String inviterName, @Nullable final String folderName,
                @Nullable final String note, final String signUpCode)
                throws IOException
        {
            String url = getSignUpLink(signUpCode);

            // TODO Ideally static email contents should be separate from Java files.
            final String subject = (folderName != null)
                    ? "Join my " + L.PRODUCT + " folder"
                    : "Invitation to " + L.PRODUCT + " (beta)";

            final Email email = new Email(subject, false, null);

            if (inviter != null) {
                composeUserInitiatedSignUpInvitationEmail(inviter, inviterName, folderName, note,
                        url, email);
            } else {
                composeAeroFSInitiatedSignUpInvitationEmail(url, email);
            }

            return new InvitationEmailer(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, inviterName, invitee, null, subject,
                            email.getTextEmail(), email.getHTMLEmail(), true,
                            EmailCategory.FOLDERLESS_INVITE);

                    EmailUtil.emailSPNotification(
                            inviter + " invited " + invitee +
                                    (folderName != null ? " to " + folderName : " folderless"),
                            "code " + signUpCode);
                    return null;
                }
            });
        }

        private void composeAeroFSInitiatedSignUpInvitationEmail(String url, Email email)
                throws IOException
        {
            String body = "\n" +
                "Hi,\n" +
                "\n" +
                "You've recently signed up to test " + L.PRODUCT + " (" + SP.WEB_BASE + ").\n" +
                "\n" +
                L.PRODUCT + " allows you to sync, share, and collaborate on files privately" +
                " and securely.\n" +
                "Any data that you put inside your " + L.PRODUCT + " folder" +
                " will be synced *only* with your personal\n" +
                "devices, and anyone you invite to share files with you.\n" +
                "\n" +
                "Please keep in mind that " + L.PRODUCT + " is still in beta! We" +
                " release updates regularly and appreciate any and all feedback.\n" +
                "\n" +
                "You can now download " + L.PRODUCT + " at:\n" +
                "\n" +
                url;

            email.addSection("You've been invited to " + L.PRODUCT + "!", HEADER_SIZE.H1, body);
            email.addDefaultSignature();
        }

        private void composeUserInitiatedSignUpInvitationEmail(String inviter, String inviterName,
                String folderName, String note, String url, Email email)
                throws IOException
        {
            String nameAndEmail = inviterName.isEmpty() ? inviter : inviterName + " (" +
                    inviter + ")";

            String body = "\n" +
                nameAndEmail + " has invited you to " +
                (folderName != null ? "a shared " + L.PRODUCT + " folder" : L.PRODUCT) +
                (note != null ? ":\n\n" + note : ".") + "\n" +
                "\n" +
                L.PRODUCT + " is a file syncing, sharing, and collaboration tool that" +
                " lets you sync files privately without using public cloud. You can learn more" +
                " about it at " + SP.WEB_BASE + "." + "\n" +
                "\n" +
                "Get started with " + L.PRODUCT + " at:\n" +
                "\n" + url;

            // If fromPerson is empty (user didn't set his name), use his email address instead
            String nameOrEmail = inviterName.isEmpty() ? inviter : inviterName;
            email.addSection(nameOrEmail + " invited you to " + L.PRODUCT + "!",
                    HEADER_SIZE.H1, body);
            email.addDefaultSignature();
        }

        public InvitationEmailer createFolderInvitationEmailer(@Nonnull final String from,
                final String to, final String fromPerson, @Nullable final String folderName,
                @Nullable final String note, final SID sid)
                throws IOException
        {
            final String subject = "Join my " + L.PRODUCT + " folder";

            final Email email = new Email(subject, false, null);

            String nameAndEmail = fromPerson.isEmpty() ? from : fromPerson + " (" + from + ")";
            String body = "\n" +
                    nameAndEmail + " has invited you to a shared " + L.PRODUCT +
                    " folder" +
                    (note != null ? (":\n\n" + note) : ".") + "\n" +
                    "\n" +
                    "Click on this link to view and accept the invitation: " +
                    ACCEPT_INVITATION_LINK;

            // If fromPerson is empty (user didn't set his name), use his email address instead
            String nameOrEmail = fromPerson.isEmpty() ? from : fromPerson;
            email.addSection(
                    nameOrEmail + " wants to share " + Util.quote(folderName) + " with you.",
                    HEADER_SIZE.H1, body);
            email.addDefaultSignature();

            return new InvitationEmailer(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS,
                            fromPerson,
                            to,
                            null,
                            subject,
                            email.getTextEmail(),
                            email.getHTMLEmail(),
                            true,
                            EmailCategory.FOLDER_INVITE
                    );

                    EmailUtil.emailSPNotification(from + " shared " + folderName + " with " + to,
                            "code " + sid.toStringFormal());

                    return null;
                }
            });
        }

        public InvitationEmailer createOrganizationInvitationEmailer(@Nonnull final User inviter,
                @Nonnull final User invitee, @Nonnull Organization organization)
                throws IOException, SQLException, ExNotFound
        {
            final String subject = "Join my team on AeroFS!";

            final Email email = new Email(subject);

            FullName inviterFullName = inviter.getFullName();
            final String inviterName, inviterLongName;
            if (inviterFullName.isFirstOrLastNameEmpty()) {
                inviterName = inviterLongName = inviter.id().toString();
            } else {
                inviterName = inviterFullName.toString();
                inviterLongName = inviterName + " (" + inviter.id().toString() + ")";
            }

            String body = "\n" +
                    inviterLongName + " has invited you to join the team on AeroFS.\n" +
                    "\n" +
                    "Click on this link to view the invitation: " + ACCEPT_INVITATION_LINK + "\n" +
                    "\n" +
                    "Once you join the team, all the files in your " + S.ROOT_ANCHOR + " will be" +
                    " synced to the team's AeroFS Team Server.\n" +
                    "\n" +
                    "If you do not wish to join the team, simply ignore this email.";

            email.addSection(subject, HEADER_SIZE.H1, body);
            email.addDefaultSignature();

            return new InvitationEmailer(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    SVClient.sendEmail(
                            SV.SUPPORT_EMAIL_ADDRESS,
                            inviterName,
                            invitee.id().toString(),
                            null,
                            subject,
                            email.getTextEmail(),
                            email.getHTMLEmail(),
                            true,
                            EmailCategory.ORGANIZATION_INVITATION
                    );

                    return null;
                }
            });
        }
    }

    private final Callable<Void> _c;

    private InvitationEmailer(@Nullable Callable<Void> c)
    {
        _c = c;
    }

    public void send() throws ExEmailSendingFailed
    {
        if (_c != null) {
            try {
                _c.call();
            } catch (Exception e) {
                throw new ExEmailSendingFailed(e);
            }
        }
    }
}
