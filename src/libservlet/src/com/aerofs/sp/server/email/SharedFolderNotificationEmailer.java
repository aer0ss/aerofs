/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.labeling.L;
import com.aerofs.lib.Util;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import org.slf4j.Logger;

import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;

public class SharedFolderNotificationEmailer
{
    private final static Logger l = Loggers.getLogger(SharedFolderNotificationEmailer.class);

    private static final AsyncEmailSender _emailSender = AsyncEmailSender.create();

    private static final String changeRoles = "\n" +
                "This email is a confirmation that %s has changed %s role in the folder from " +
                "%s to %s:\n%s\n" +
                "If you'd like to find out more about the different " + L.brand() + " roles, " +
                // Whitespace required after URL for autolinker. TODO (WW) fix this!
                "please take a look at https://support.aerofs.com/entries/22831810 .";

    private static final String deleteFromFolder = "\n" +
            "This email is a confirmation that %s has removed %s from the folder. %s, you will need a" +
            "new invitation to the folder to rejoin it.";

    /**
     * Notify the inviter of a shared folder when the person he/she invited accepts the invitation
     */
    public void sendInvitationAcceptedNotificationEmail(SharedFolder sf, User inviter, User invitee)
            throws IOException, MessagingException, SQLException, ExNotFound

    {
        NameFormatter nfInvitee = new NameFormatter(invitee);

        String quotedFolderName = Util.quote(sf.getName(inviter));
        String subject = nfInvitee.nameOnly() + " accepted your invitation to " +
                quotedFolderName;

        String body = "\n" +
                nfInvitee.nameAndEmail() + " has accepted your invitation to the shared" +
                " folder " + quotedFolderName + ".\n" +
                "\n" +
                "To view or manage the members of your shared folders, please go to " +
                // For autolinker to work, either leave a whitespace between the URL and the period
                // for the end of sentence, or don't add periods at all. TODO (WW) fix this!
                WWW.SHARED_FOLDERS_URL;

                Email email = new Email();
        email.addSection(subject, body);
        email.addDefaultSignature();

        _emailSender.sendPublicEmailFromSupport(SPParam.EMAIL_FROM_NAME, inviter.id().getString(),
                null, subject, email.getTextEmail(), email.getHTMLEmail());
    }

    private static void describePermissionChange(StringBuilder bd, int diff, String base)
    {
        for (Permission p : Permission.values()) {
            if ((diff & p.flag()) != 0) bd.append(base).append(p.description()).append(".\n");
        }
    }

    public void sendRoleChangedNotificationEmail(SharedFolder sf, User changer, User subject,
            Permissions oldPermissions, Permissions newPermissions)
            throws IOException, MessagingException, SQLException, ExNotFound
    {
        if (oldPermissions.equals(newPermissions)) {
            l.info("no change, no email sent to {}", subject);
            return;
        }

        String title = " Your role in the folder " + Util.quote(sf.getName(subject)) + " has changed";
        String body = String.format(changeRoles, changerName(changer), "your", oldPermissions.roleName(),
                newPermissions.roleName(), describeRoleChanges(oldPermissions, newPermissions));

        sendEmail(subject, title, body);
    }

    public void sendRoleChangedNotificationEmail(SharedFolder sf, User changer, Group group,
            Permissions oldPermissions, Permissions newPermissions)
            throws IOException, MessagingException, SQLException, ExNotFound
    {
        if (oldPermissions.equals(newPermissions)) {
            l.info("no change, no emails sent to members of group {}", group);
            return;
        }

        for (User subject : group.listMembers()) {
            String title = " Your group " + group.getCommonName() + "'s role in the folder " +
                    Util.quote(sf.getName(subject)) + " has changed";
            String body = String.format(changeRoles, changerName(changer) , "your group's",
                    oldPermissions.roleName(), newPermissions.roleName(),
                    describeRoleChanges(oldPermissions, newPermissions));

            sendEmail(subject, title, body);
        }
    }

    public void sendRemovedFromFolderNotificationEmail(SharedFolder sf, User changer, User subject)
            throws ExNotFound, SQLException, IOException, MessagingException
    {
        String title = "You have been removed from the folder " + Util.quote(sf.getName(subject));
        String body = String.format(deleteFromFolder, changerName(changer), "you",
                "If you are not in a group that is a member of this folder");

        sendEmail(subject, title, body);
    }

    public void sendRemovedFromFolderNotificationEmail(SharedFolder sf, User changer, Group group)
            throws ExNotFound, SQLException, IOException, MessagingException
    {
        for (User subject : group.listMembers()) {
            String title = "Your group " + group.getCommonName() +
                    " has been removed from the folder " + Util.quote(sf.getName(subject));
            String body = String.format(deleteFromFolder, changerName(changer), "your group",
                    "If you are not directly a member of this folder or in another group that is");

            sendEmail(subject, title, body);
        }
    }

    private void sendEmail(User to, String title, String body)
        throws IOException, MessagingException
    {

        Email email = new Email();
        email.addSection(title, body);
        email.addDefaultSignature();

        _emailSender.sendPublicEmailFromSupport(SPParam.EMAIL_FROM_NAME,
                to.id().getString(), null, title, email.getTextEmail(), email.getHTMLEmail());
    }

    private StringBuilder describeRoleChanges(Permissions oldPermissions, Permissions newPermissions)
    {
        StringBuilder roleChanges = new StringBuilder();
        describePermissionChange(roleChanges,
                oldPermissions.bitmask() & ~newPermissions.bitmask(),
                "- You are no longer allowed to ");
        describePermissionChange(roleChanges,
                newPermissions.bitmask() & ~oldPermissions.bitmask(),
                "- You are now allowed to ");
        return roleChanges;
    }

    private String changerName(User changer)
            throws ExNotFound, SQLException
    {
        NameFormatter nfChanger = new NameFormatter(changer);
        return changer.id().isTeamServerID() ? "an organization admin" :
                "a folder owner (" + nfChanger.nameOnly() + ")";
    }

}
