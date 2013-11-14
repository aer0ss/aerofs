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
    private static final AsyncEmailSender _emailSender = AsyncEmailSender.create();

    public static class Factory
    {
        /**
         * @param folderName non-null if the invitation is triggered by sharing a folder, in which
         *      case {@code role} must be non-null as well.
         * @param signUpCode null if the user is auto-provisioned.
         */
        public InvitationEmailer createSignUpInvitationEmailer(final User inviter,
                final User invitee, @Nullable final String folderName, @Nullable Role role,
                @Nullable String note, @Nullable final String signUpCode)
                throws IOException, SQLException, ExNotFound
        {
            final InvitationEmailContentStrategy cs = new InvitationEmailContentStrategy(
                    invitee.id(), folderName, role, note, signUpCode);

            final Email email = new Email();
            final NameFormatter nsInviter = new NameFormatter(inviter);

            composeSignUpInvitationEmail(cs, nsInviter, email);

            return new InvitationEmailer(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    _emailSender.sendPublicEmailFromSupport(nsInviter.nameOnly(),
                            invitee.id().getString(), getReplyTo(inviter), cs.subject(),
                            email.getTextEmail(), email.getHTMLEmail(),
                            EmailCategory.FOLDERLESS_INVITE);

                    EmailUtil.emailInternalNotification(inviter + " invited " + invitee +
                            " to " + folderName, "code " + signUpCode);
                    return null;
                }
            });
        }

        private void composeSignUpInvitationEmail(InvitationEmailContentStrategy cs,
                NameFormatter nsInviter, Email email)
                throws IOException
        {
            String body = "\n" +
                nsInviter.nameAndEmail() + " has invited you to " +
                cs.invitedTo() + cs.noteAndEndOfSentence() + "\n" +
                "\n" +
                L.brand() + " is a file syncing, sharing, and collaboration tool that" +
                " lets you sync files privately without using public cloud. You can learn more" +
                    // Whitespace required after URL for autolinker. TODO (WW) fix this!
                " about it at " + WWW.MARKETING_HOST_URL + " ." + "\n" +
                "\n" +
                "Get started with " + L.brand() + " at:\n" +
                "\n" + cs.signUpURLAndInstruction();

            // If fromPerson is empty (user didn't set his name), use his email address instead
            email.addSection(nsInviter.nameOnly() + " invited you to " + L.brand() + "!",
                    body);
            email.addDefaultSignature();
        }

        public InvitationEmailer createFolderInvitationEmailer(@Nonnull final User sharer,
                final User sharee, @Nullable final String folderName,
                @Nullable final String note, final SID sid, Role role)
                throws IOException, SQLException, ExNotFound
        {
            final Email email = new Email();
            final NameFormatter nsSharer = new NameFormatter(sharer);
            final InvitationEmailContentStrategy cs =
                    new InvitationEmailContentStrategy(sharee.id(), folderName, role, note, null);

            String body = "\n" +
                    nsSharer.nameAndEmail() + " has invited you to a shared " + L.brand() +
                    " folder as " + WordUtils.capitalizeFully(role.getDescription()) +
                    cs.noteAndEndOfSentence() + "\n" +
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
                            sharee.id().getString(), getReplyTo(sharer), cs.subject(),
                            email.getTextEmail(), email.getHTMLEmail(), EmailCategory.FOLDER_INVITE);

                    EmailUtil.emailInternalNotification(
                            sharer + " shared " + folderName + " with " + sharee,
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
        return inviter.id().isTeamServerID() ? WWW.SUPPORT_EMAIL_ADDRESS : inviter.id().getString();
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
