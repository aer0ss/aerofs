/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.id.SID;
import com.aerofs.lib.FullName;
import com.aerofs.labeling.L;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExEmailSendingFailed;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.servlets.lib.EmailSender;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sv.common.EmailCategory;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.base.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.Callable;

// TODO (WW) the pattern of this class is inconsistent with PasswordResetEmailer. Need refactoring
// and/or merging.
public class InvitationEmailer
{
    private final static String ACCEPT_INVITATION_LINK = WWW.DASHBOARD_HOST_URL.get() + "/accept";

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
        public InvitationEmailer createSignUpInvitationEmailer(final User inviter,
                final User invitee, @Nullable final String folderName, @Nullable String note,
                final String signUpCode)
                throws IOException, SQLException, ExNotFound
        {
            String url = RequestToSignUpEmailer.getSignUpLink(signUpCode);

            // TODO Ideally static email contents should be separate from Java files.
            final String subject = (folderName != null)
                    ? "Join my " + L.brand() + " folder"
                    : "Invitation to " + L.brand();

            final Email email = new Email(subject, false, null);
            final NameStrings nsInviter = new NameStrings(inviter);

            composeSignUpInvitationEmail(nsInviter, folderName, note, url, email);

            return new InvitationEmailer(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    EmailSender.sendEmail(WWW.SUPPORT_EMAIL_ADDRESS.get(), nsInviter.nameOnly(),
                            invitee.id().getString(), null, subject, email.getTextEmail(),
                            email.getHTMLEmail(), true, EmailCategory.FOLDERLESS_INVITE);

                    EmailUtil.emailSPNotification(
                            inviter + " invited " + invitee +
                                    (folderName != null ? " to " + folderName : " folderless"),
                            "code " + signUpCode);
                    return null;
                }
            });
        }

        private void composeSignUpInvitationEmail(NameStrings nsInviter, String folderName,
                String note, String url, Email email)
                throws IOException
        {
            String body = "\n" +
                nsInviter.nameAndEmail() + " has invited you to " +
                (folderName != null ? "a shared " + L.brand() + " folder " + Util.quote(folderName)
                         : L.brand()) +
                (isNoteEmpty(note) ? "." : ":\n\n" + note) + "\n" +
                "\n" +
                L.brand() + " is a file syncing, sharing, and collaboration tool that" +
                " lets you sync files privately without using public cloud. You can learn more" +
                " about it at " + WWW.MARKETING_HOST_URL.get() + "." + "\n" +
                "\n" +
                "Get started with " + L.brand() + " at:\n" +
                "\n" + url;

            // If fromPerson is empty (user didn't set his name), use his email address instead
            email.addSection(nsInviter.nameOnly() + " invited you to " + L.brand() + "!",
                    HEADER_SIZE.H1, body);
            email.addDefaultSignature();
        }

        private boolean isNoteEmpty(@Nullable String note)
        {
            return Strings.nullToEmpty(note).trim().isEmpty();
        }

        public InvitationEmailer createFolderInvitationEmailer(@Nonnull final User sharer,
                final User sharee, @Nullable final String folderName,
                @Nullable final String note, final SID sid)
                throws IOException, SQLException, ExNotFound
        {
            final String subject = "Join my " + L.brand() + " folder";

            final Email email = new Email(subject, false, null);

            final NameStrings nsSharer = new NameStrings(sharer);

            String body = "\n" +
                    nsSharer.nameAndEmail() + " has invited you to a shared " + L.brand() +
                    " folder" +
                    (isNoteEmpty(note) ? "." : (":\n\n" + note)) + "\n" +
                    "\n" +
                    "Click on this link to view and accept the invitation: " +
                    ACCEPT_INVITATION_LINK;

            email.addSection(
                    nsSharer.nameOnly() + " wants to share " + Util.quote(folderName) + " with you.",
                    HEADER_SIZE.H1, body);
            email.addDefaultSignature();

            return new InvitationEmailer(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    EmailSender.sendEmail(WWW.SUPPORT_EMAIL_ADDRESS.get(),
                            nsSharer.nameOnly(),
                            sharee.id().getString(),
                            null,
                            subject,
                            email.getTextEmail(),
                            email.getHTMLEmail(),
                            true,
                            EmailCategory.FOLDER_INVITE
                    );

                    EmailUtil.emailSPNotification(sharer + " shared " + folderName + " with " + sharee,
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

            final NameStrings ns = new NameStrings(inviter);

            String body = "\n" +
                    ns.nameAndEmail() + " has invited you to join the team on AeroFS.\n" +
                    "\n" +
                    "Click on this link to view the invitation: " + ACCEPT_INVITATION_LINK + "\n" +
                    "\n" +
                    "If you do not wish to join the team, simply ignore this email.";

            email.addSection(subject, HEADER_SIZE.H1, body);
            email.addDefaultSignature();

            return new InvitationEmailer(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    EmailSender.sendEmail(
                            WWW.SUPPORT_EMAIL_ADDRESS.get(),
                            ns.nameOnly(),
                            invitee.id().getString(),
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

        private class NameStrings
        {
            private String _inviterName;
            private String _inviterLongName;

            public NameStrings(User inviter)
                    throws SQLException, ExNotFound
            {
                FullName inviterFullName = inviter.getFullName();
                if (inviterFullName.isFirstOrLastNameEmpty()) {
                    _inviterName = _inviterLongName = inviter.id().getString();
                } else {
                    _inviterName = inviterFullName.toString();
                    _inviterLongName = _inviterName + " (" + inviter.id().getString() + ")";
                }
            }

            public String nameOnly()
            {
                return _inviterName;
            }

            public String nameAndEmail()
            {
                return _inviterLongName;
            }
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
