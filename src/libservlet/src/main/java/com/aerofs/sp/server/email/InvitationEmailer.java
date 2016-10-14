/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.acl.Permissions;
import com.aerofs.ids.SID;
import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.user.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;
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
         * @param signUpCode null if the user is auto-provisioned.
         */
        public InvitationEmailer createSharedFolderSignUpInvitationEmailer(final User inviter,
                final User invitee, final String folderName, Permissions permissions,
                @Nullable String note, @Nullable final String signUpCode)
                throws IOException, SQLException, ExNotFound
        {
            final InvitationEmailContentStrategy cs = new InvitationEmailContentStrategy(
                    invitee.id(), folderName, permissions, note, null, signUpCode);

            final Email email = new Email();
            final NameFormatter nsInviter = new NameFormatter(inviter);

            composeSignUpInvitationEmail(cs, nsInviter, email);

            return new InvitationEmailer(() -> {
                _emailSender.sendPublicEmailFromSupport(SPParam.BRAND,
                        invitee.id().getString(), getReplyTo(inviter), cs.subject(),
                        email.getTextEmail(), email.getHTMLEmail());
                return null;
            });
        }

        public InvitationEmailer createSignUpInvitationEmailer(final User inviter,
                final User invitee, @Nullable final String signUpCode)
                throws SQLException, ExNotFound, IOException
        {
            final InvitationEmailContentStrategy cs = new InvitationEmailContentStrategy(
                    invitee.id(), null, null, null, null, signUpCode);

            final Email email = new Email();
            final NameFormatter nsInviter = new NameFormatter(inviter);

            composeSignUpInvitationEmail(cs, nsInviter, email);

            return new InvitationEmailer(() -> {
                _emailSender.sendPublicEmailFromSupport(SPParam.BRAND,
                        invitee.id().getString(), getReplyTo(inviter), cs.subject(),
                        email.getTextEmail(), email.getHTMLEmail());
                return null;
            });
        }

        public InvitationEmailer createGroupSignUpInvitationEmailer(final User inviter,
                final User invitee, final Group group, @Nullable final String signUpCode)
                throws SQLException, ExNotFound, IOException
        {
            final InvitationEmailContentStrategy cs = new InvitationEmailContentStrategy(
                    invitee.id(), null, null, null, group.getCommonName(), signUpCode);

            final Email email = new Email();
            final NameFormatter nsInviter = new NameFormatter(inviter);

            composeSignUpInvitationEmail(cs, nsInviter, email);

            return new InvitationEmailer(() -> {
                _emailSender.sendPublicEmailFromSupport(SPParam.BRAND,
                        invitee.id().getString(), getReplyTo(inviter), cs.subject(),
                        email.getTextEmail(), email.getHTMLEmail());
                return null;
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
                SPParam.BRAND + " is a file syncing, sharing, and collaboration tool that" +
                " lets you sync files privately without using the public cloud. You can learn more" +
                    // Whitespace required after URL for autolinker. TODO (WW) fix this!
                " about it at " + WWW.MARKETING_HOST_URL + " ." + "\n" +
                "\n" +
                "Get started with " + SPParam.BRAND + " at:\n" +
                "\n" + cs.signUpURLAndInstruction();

            // If fromPerson is empty (user didn't set his name), use his email address instead
            email.addSection(nsInviter.nameOnly() + " Invited You to " + SPParam.BRAND + "!",
                    body);
            email.addDefaultSignature();
        }

        public InvitationEmailer createFolderInvitationEmailer(final User sharer,
                final User sharee, final String folderName, @Nullable final String note,
                final SID sid, final Permissions permissions)
                throws
                IOException,
                SQLException,
                ExNotFound
        {
            final Email email = new Email();
            final NameFormatter nsSharer = new NameFormatter(sharer);
            final InvitationEmailContentStrategy cs = new InvitationEmailContentStrategy(
                    sharee.id(), folderName, permissions, note, null, null);

            String body = "\n" +
                    nsSharer.nameAndEmail() + " has invited you to a shared " + SPParam.BRAND +
                    " folder as " + permissions.roleName() + cs.noteAndEndOfSentence() + "\n" +
                    "\n" +
                    "Click on this link to view and accept the invitation: " +
                    ACCEPT_INVITATION_LINK;

            String title = nsSharer.nameOnly() + " Wants to Share " + Util.quote(folderName) +
                    " With You";

            email.addSection(title, body);
            email.addDefaultSignature();

            return new InvitationEmailer(() -> {
                _emailSender.sendPublicEmailFromSupport(SPParam.BRAND,
                        sharee.id().getString(), getReplyTo(sharer), cs.subject(),
                        email.getTextEmail(), email.getHTMLEmail());
                return null;
            });
        }

        public InvitationEmailer createAddedToGroupEmailer(final User changer, final User newMember,
                final Group group)
                throws
                IOException,
                SQLException,
                ExNotFound
        {
            final Email email = new Email();
            final NameFormatter nf = new NameFormatter(changer);
            String quotedGroupName = Util.quote(group.getCommonName());

            String body = "\n" +
                    nf.nameAndEmail() + " has added you to the group " + quotedGroupName + ".\n" +
                    "From now on you will be invited to any shared folders that " +
                    quotedGroupName + " joins.";
            String title = "You Are Now a Member of The Group " + quotedGroupName;

            email.addSection(title, body);
            email.addDefaultSignature();

            return new InvitationEmailer(() -> {
                _emailSender.sendPublicEmailFromSupport(SPParam.BRAND,
                        newMember.id().getString(), getReplyTo(changer), title,
                        email.getTextEmail(), email.getHTMLEmail());
                return null;
            });
        }

        public InvitationEmailer createBatchInvitationEmailer(final User sharer, final User sharee,
                final Group group, Set<SharedFolder> sharedFolders)
                throws
                IOException,
                SQLException,
                ExNotFound
        {
            assert sharedFolders.size() > 0;
            final Email email = new Email();
            final NameFormatter nf = new NameFormatter(sharer);
            String folderOrFolders = sharedFolders.size() == 1 ? "folder" : "folders";
            String inviteOrInvites = sharedFolders.size() == 1 ? "invitation" : "invitations";
            String quotedGroupName = Util.quote(group.getCommonName());

            //TODO (RD) include names of the folders in the email
            String body = "\n" +
                    nf.nameAndEmail() + " has added you to the group " + quotedGroupName + ".\n" +
                    "As part of being added to the group " + quotedGroupName +
                    ", you've been invited to " + sharedFolders.size() + " " + SPParam.BRAND +
                    " shared " + folderOrFolders + ".\n" +
                    "From now on you will also be invited to any shared folders that " +
                    quotedGroupName + " joins.\n\n" +
                    "Click on this link to view and accept the " + inviteOrInvites + ":\n " +
                    ACCEPT_INVITATION_LINK;

            String title = "You Are Now a Member of The Group " + quotedGroupName;

            email.addSection(title, body);
            email.addDefaultSignature();

            return new InvitationEmailer(() -> {
                _emailSender.sendPublicEmailFromSupport(SPParam.BRAND,
                        sharee.id().getString(), getReplyTo(sharer), title,
                        email.getTextEmail(), email.getHTMLEmail());
                return null;
            });
        }

        public InvitationEmailer createOrganizationInvitationEmailer(@Nonnull final User inviter,
                @Nonnull final User invitee)
                throws IOException, SQLException, ExNotFound
        {
            final String subject = "Join My Organization on AeroFS!";
            final NameFormatter ns = new NameFormatter(inviter);
            String body = "\n" +
                    ns.nameAndEmail() + " has invited you to join their organization on AeroFS.\n" +
                    "\n" +
                    "Click on this link to view the invitation:\n " + ACCEPT_INVITATION_LINK + "\n" +
                    "\n" +
                    "If you do not wish to join the organization, simply ignore this email.";

            final Email email = new Email();
            email.addSection(subject, body);
            email.addDefaultSignature();

            return new InvitationEmailer(() -> {
                _emailSender.sendPublicEmailFromSupport(SPParam.BRAND, invitee.id().getString(),
                        getReplyTo(inviter), subject, email.getTextEmail(),
                        email.getHTMLEmail());

                return null;
            });
        }

        public InvitationEmailer doesNothing()
        {
            return new InvitationEmailer(() -> null);
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
