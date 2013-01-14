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
import com.aerofs.sp.server.AdminPanelParam;
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

        public InvitationEmailer createSignUpInvitationEmailer(@Nullable final String inviter,
                final String invitee, final String inviterName, @Nullable final String folderName,
                @Nullable final String note, final String signupCode)
                throws IOException
        {
            String url = SPParam.getWebDownloadLink(signupCode);

            // TODO Ideally static email contents should be separate from Java files.
            final String subject = (folderName != null)
                    ? "Join my " + L.PRODUCT + " folder"
                    : "Invitation to " + L.PRODUCT + " (beta)";

            String intro = L.PRODUCT + " is a file syncing, sharing, and collaboration tool that " +
                "lets you sync files without using cloud servers. You can learn more about it at " +
                "" + SP.WEB_BASE + ".";

            String body;
            final Email email = new Email(subject, false, null);

            if (inviter != null) {
                String nameAndEmail = inviterName.isEmpty() ? inviter : inviterName + " (" +
                        inviter + ")";
                body = "\n" + nameAndEmail + " has invited you to "
                    + (folderName != null ? "a shared " + L.PRODUCT + " folder" : L.PRODUCT)
                    + (note != null ? ":\n\n" + note : ".") + "\n\n"
                    + intro + "\n\n" +
                    "Download " + L.PRODUCT + " at:\n\n" + url + "\n\n" +
                    "And when prompted, enter the following invitation code:\n\n" + signupCode;

                // If fromPerson is empty (user didn't set his name), use his email address instead
                String nameOrEmail = inviterName.isEmpty() ? inviter : inviterName;
                email.addSection(nameOrEmail + " invited you to " + L.PRODUCT + "!",
                        HEADER_SIZE.H1, body);

                email.addSignature("Best Regards,", "The " + L.PRODUCT + " Team",
                        "Have questions or comments? Email us at " + SV.SUPPORT_EMAIL_ADDRESS);
            } else {
                body = "\nHi,\n\n" +
                    "You've recently signed up to test " + L.PRODUCT + " - file syncing without " +
                    "servers (" + SP.WEB_BASE + ").\n\n" + L.PRODUCT + " allows you to sync, " +
                    "share, and collaborate on files privately and " +
                    "securely.\n" + "Any data that you put inside your " + L.PRODUCT + " folder " +
                    "will be synced *only* with your personal\n" +
                    "devices, and anyone you invite to share files with you.\n\n" +
                    "Please keep in mind that " + L.PRODUCT + " is still in beta! We " +
                    "release updates regularly and appreciate any and all feedback.\n\n" +
                    "You can now download " + L.PRODUCT + " at:\n\n" + url + "\n\n" +
                    "And when prompted, enter the following invitation code:\n\n" +
                    signupCode;

                email.addSection("You've been invited to " + L.PRODUCT + "!",
                        HEADER_SIZE.H1,
                        body);

                email.addSignature("Happy Syncing :)", inviterName,
                        "p.s. Let us know what you think at " + SV.SUPPORT_EMAIL_ADDRESS +
                        ". We'd love to hear your feedback!");
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
                            "code " + signupCode);
                    return null;
                }
            });
        }

        public InvitationEmailer createFolderInvitationEmailer(@Nonnull final String from,
                final String to, final String fromPerson, @Nullable final String folderName,
                @Nullable final String note, final SID sid)
                throws IOException
        {
            final String subject = "Join my " + L.PRODUCT + " folder";

            final Email email = new Email(subject, false, null);

            String url = SPParam.getWebDownloadLink(sid.toStringFormal());
            // TODO: add link to "pending invitations" page

            String nameAndEmail = fromPerson.isEmpty() ? from : fromPerson + " (" + from + ")";
            String body = "\n" + nameAndEmail + " has invited you to a shared " + L.PRODUCT +
                    " folder" +
                    (note != null ? (":\n\n" + note) : ".") +
                    "\n\n" +
                    "Click on this link to view and accept the invitation: " +
                    AdminPanelParam.ADMIN_ACCEPT_LINK +
                    "\n\n" +
                    "You can download " + L.PRODUCT + " at " + url + ".";

            // If fromPerson is empty (user didn't set his name), use his email address instead
            String nameOrEmail = fromPerson.isEmpty() ? from : fromPerson;
            email.addSection(nameOrEmail + " wants to share " + Util.quote(folderName) + " with you.",
                    HEADER_SIZE.H1, body);

            email.addSignature("Best Regards,", "The " + L.PRODUCT + " Team", Email.DEFAULT_PS);

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

            String body = "\n" + inviterLongName + " has invited you to join the team on AeroFS." +
                    "\n\n" +
                    "Click on this link to view the " +
                    "invitation: " + AdminPanelParam.ADMIN_ACCEPT_LINK +
                    "\n\n" +
                    "Once you join the team, all the files in your " + S.ROOT_ANCHOR + " will be" +
                    " synced to the team's AeroFS Team Server." +
                    "\n\n" +
                    "If you do not wish to join the team, simply ignore this email.";

            email.addSection(subject, HEADER_SIZE.H1, body);
            email.addSignature("Best Regards,", "The " + L.PRODUCT + " Team", Email.DEFAULT_PS);

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
