/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.acl.Role;
import com.aerofs.base.id.SID;
import com.aerofs.labeling.L;
import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sv.common.EmailCategory;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.base.Strings;
import org.apache.commons.lang.WordUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.Callable;

/**
 * Unlike other emailer classses, this class requires the factory pattern since invitation emails
 * may be generated in a transaction, and actual sending of the emails happens after the
 * transaction. Created emailer objects carry email information across transaction and method
 * boundaries.
 */
public class InvitationEmailer
{
    private final static String ACCEPT_INVITATION_LINK = WWW.DASHBOARD_HOST_URL + "/accept";
    private static final AsyncEmailSender _emailSender = new AsyncEmailSender();

    public static class Factory
    {
        /**
         * @param inviter null if the invite is sent from the AeroFS team.
         *
         * TODO (WW) use a separate method rather than a null inviter for AeroFS initiated invites.
         */
        public InvitationEmailer createSignUpInvitationEmailer(final User inviter,
                final User invitee, @Nullable final String folderName, @Nullable Role role,
                @Nullable String note, final String signUpCode)
                throws IOException, SQLException, ExNotFound
        {
            String url = RequestToSignUpEmailer.getSignUpLink(signUpCode);

            // TODO Ideally static email contents should be separate from Java files.
            final String subject = (folderName != null)
                    ? "Join my " + L.brand() + " folder"
                    : "Invitation to " + L.brand();

            final Email email = new Email();
            final NameFormatter nsInviter = new NameFormatter(inviter);

            composeSignUpInvitationEmail(nsInviter, folderName, role, note, url, email);

            return new InvitationEmailer(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    _emailSender.sendPublicEmailFromSupport(nsInviter.nameOnly(),
                            invitee.id().getString(), getReplyTo(inviter), subject,
                            email.getTextEmail(), email.getHTMLEmail(),
                            EmailCategory.FOLDERLESS_INVITE);

                    EmailUtil.emailSPNotification(
                            inviter + " invited " + invitee +
                                    (folderName != null ? " to " + folderName : " folderless"),
                            "code " + signUpCode);
                    return null;
                }
            });
        }

        private void composeSignUpInvitationEmail(NameFormatter nsInviter, String folderName,
                Role role, String note, String url, Email email)
                throws IOException
        {
            String body = "\n" +
                nsInviter.nameAndEmail() + " has invited you to " +
                (folderName != null ? "a shared " + L.brand() + " folder " + Util.quote(folderName) +
                        " as " + WordUtils.capitalizeFully(role.getDescription())
                         : L.brand()) +
                (isNoteEmpty(note) ? "." : ":\n\n" + note) + "\n" +
                "\n" +
                L.brand() + " is a file syncing, sharing, and collaboration tool that" +
                " lets you sync files privately without using public cloud. You can learn more" +
                    // Whitespace required after URL for autolinker
                " about it at " + WWW.MARKETING_HOST_URL + " ." + "\n" +
                "\n" +
                "Get started with " + L.brand() + " at:\n" +
                "\n" + url;

            // If fromPerson is empty (user didn't set his name), use his email address instead
            email.addSection(nsInviter.nameOnly() + " invited you to " + L.brand() + "!",
                    body);
            email.addDefaultSignature();
        }

        private boolean isNoteEmpty(@Nullable String note)
        {
            return Strings.nullToEmpty(note).trim().isEmpty();
        }

        public InvitationEmailer createFolderInvitationEmailer(@Nonnull final User sharer,
                final User sharee, @Nullable final String folderName,
                @Nullable final String note, final SID sid, Role role)
                throws IOException, SQLException, ExNotFound
        {
            final String subject = "Join my " + L.brand() + " folder";

            final Email email = new Email();

            final NameFormatter nsSharer = new NameFormatter(sharer);

            String body = "\n" +
                    nsSharer.nameAndEmail() + " has invited you to a shared " + L.brand() +
                    " folder as " + WordUtils.capitalizeFully(role.getDescription()) +
                    (isNoteEmpty(note) ? "." : (":\n\n" + note)) + "\n" +
                    "\n" +
                    "Click on this link to view and accept the invitation: " +
                    ACCEPT_INVITATION_LINK;

            email.addSection(
                    nsSharer.nameOnly() + " wants to share " + Util.quote(folderName) + " with you.",
                    body);
            email.addDefaultSignature();

            return new InvitationEmailer(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    _emailSender.sendPublicEmailFromSupport(nsSharer.nameOnly(),
                            sharee.id().getString(), getReplyTo(sharer), subject,
                            email.getTextEmail(), email.getHTMLEmail(), EmailCategory.FOLDER_INVITE);

                    EmailUtil.emailSPNotification(sharer + " shared " + folderName + " with " + sharee,
                            "code " + sid.toStringFormal());

                    return null;
                }
            });
        }

        public InvitationEmailer createOrganizationInvitationEmailer(@Nonnull final User inviter,
                @Nonnull final User invitee)
                throws IOException, SQLException, ExNotFound
        {
            final String subject = "Join my team on AeroFS!";
            final NameFormatter ns = new NameFormatter(inviter);
            String body = "\n" +
                    ns.nameAndEmail() + " has invited you to join the team on AeroFS.\n" +
                    "\n" +
                    "Click on this link to view the invitation: " + ACCEPT_INVITATION_LINK + "\n" +
                    "\n" +
                    "If you do not wish to join the team, simply ignore this email.";

            final Email email = new Email();
            email.addSection(subject, body);
            email.addDefaultSignature();

            return new InvitationEmailer(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    _emailSender.sendPublicEmailFromSupport(ns.nameOnly(), invitee.id().getString(),
                            getReplyTo(inviter), subject, email.getTextEmail(),
                            email.getHTMLEmail(), EmailCategory.ORGANIZATION_INVITATION);

                    return null;
                }
            });
        }

    }

    private static String getReplyTo(User inviter)
    {
        return inviter.id().isTeamServerID() ?
                WWW.SUPPORT_EMAIL_ADDRESS : inviter.id().getString();
    }

    private final @Nonnull Callable<Void> _call;

    private InvitationEmailer(@Nonnull Callable<Void> call)
    {
        _call = call;
    }

    public void send() throws Exception
    {
        _call.call();
    }

}
