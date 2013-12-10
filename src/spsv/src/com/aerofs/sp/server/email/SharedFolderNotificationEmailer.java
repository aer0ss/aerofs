/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.labeling.L;
import com.aerofs.lib.Util;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sv.common.EmailCategory;
import org.apache.commons.lang.WordUtils;

import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;

public class SharedFolderNotificationEmailer
{
    private static final AsyncEmailSender _emailSender = AsyncEmailSender.create();

    /**
     * Notify the inviter of a shared folder when the person he/she invited accepts the invitation
     */
    public void sendInvitationAcceptedNotificationEmail(SharedFolder sf, User inviter, User invitee)
            throws IOException, MessagingException, SQLException, ExNotFound

    {
        NameFormatter nfInvitee = new NameFormatter(invitee);

        String quotedFolderName = Util.quote(sf.getName());
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
                null, subject, email.getTextEmail(), email.getHTMLEmail(),
                EmailCategory.SHARED_FOLDER_INVITATION_ACCEPTED_NOTIFICATION);
    }

    public void sendRoleChangedNotificationEmail(SharedFolder sf, User changer, User subject,
            Role oldRole, Role newRole)
            throws IOException, MessagingException, SQLException, ExNotFound

    {
        NameFormatter nfChanger = new NameFormatter(changer);
        String oldRoleStr = WordUtils.capitalizeFully(oldRole.getDescription());
        String newRoleStr = WordUtils.capitalizeFully(newRole.getDescription());
        String title = " Your role in the folder " + Util.quote(sf.getName()) + " has changed to " +
                newRoleStr;
        String body = "\n" +
                "This email is a confirmation that " +
                (changer.id().isTeamServerID() ? "an organization admin" : "a folder owner (" + nfChanger.nameOnly() + ")") +
                "has changed your role in the folder from " + oldRoleStr + " to " + newRoleStr + ".\n" +
                "\n" +
                "If you'd like to find out more about the different " + L.brand() + " roles, " +
                // Whitespace required after URL for autolinker. TODO (WW) fix this!
                "please take a look at https://support.aerofs.com/entries/22831810 .";

        Email email = new Email();
        email.addSection(title, body);
        email.addDefaultSignature();

        _emailSender.sendPublicEmailFromSupport(SPParam.EMAIL_FROM_NAME, subject.id().getString(),
                null, title, email.getTextEmail(), email.getHTMLEmail(),
                EmailCategory.SHARED_FOLDER_ROLE_CHANGE_NOTIFICATION);
    }
}
