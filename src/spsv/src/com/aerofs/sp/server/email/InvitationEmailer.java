/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.lib.Param.SP;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.lib.spsv.sendgrid.Sendgrid.Category;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;
import com.aerofs.servletlib.sp.SPParam;

import javax.annotation.Nullable;

import static com.aerofs.sp.server.SPSVParam.*;

/**
 * Extendable (mockable) class that sends invitation emails
 */
public class InvitationEmailer
{

    public void sendUserInvitationEmail(@Nullable String from, String to, String fromPerson,
            @Nullable String folderName, @Nullable String note, String signupCode)
            throws Exception
    {
        String url = SPParam.getWebDownloadLink(signupCode, false);

        // TODO Ideally static email contents should be separate from Java files.
        String subject = (folderName != null)
                ? "Join my " + S.PRODUCT + " folder"
                : "Invitation to " + S.PRODUCT + " (beta)";

        String intro = S.PRODUCT + " is a file syncing, sharing, and collaboration tool that " +
                "lets you sync files without using cloud servers. You can learn more about it at " +
                "" + SP.WEB_BASE + ".";

        String body;
        Email email = new Email(subject);

        if (from != null) {
            String nameAndEmail = fromPerson.isEmpty() ? from : fromPerson + " (" + from + ")";
            body = "\n" + nameAndEmail + " has invited you to "
                    + (folderName != null ? "a shared " + S.PRODUCT + " folder" : S.PRODUCT)
                    + (note != null ? ":\n\n" + note : ".") + "\n\n"
                    + intro + "\n\n" +
                    "Download " + S.PRODUCT + " at:\n\n" + url + "\n\n" +
                    "And when prompted, enter the following invitation code:\n\n" + signupCode;

            // If fromPerson is empty (user didn't set his name), use his email address instead
            String nameOrEmail = fromPerson.isEmpty() ? from : fromPerson;
            email.addSection(nameOrEmail + " invited you to " + S.PRODUCT + "!",
                    HEADER_SIZE.H1, body);

            email.addSignature("Best Regards,", "The " + S.PRODUCT + " Team",
                    "Have questions or comments? Email us at " + SP_EMAIL_ADDRESS);
        } else {
            body = "\nHi,\n\n" +
                    "You've recently signed up to test " + S.PRODUCT + " - file syncing without " +
                    "servers (" + SP.WEB_BASE + ").\n\n" + S.PRODUCT + " allows you to sync, " +
                    "share, and collaborate on files privately and " +
                    "securely.\n" + "Any data that you put inside your " + S.PRODUCT + " folder " +
                    "will be synced *only* with your personal\n" +
                    "devices, and anyone you invite to share files with you.\n\n" +
                    "Please keep in mind that " + S.PRODUCT + " is still in beta! We " +
                    "release updates regularly and appreciate any and all feedback.\n\n" +
                    "You can now download " + S.PRODUCT + " at:\n\n" + url + "\n\n" +
                    "And when prompted, enter the following invitation code:\n\n" +
                    signupCode;

            email.addSection("You've been invited to " + S.PRODUCT + "!",
                    HEADER_SIZE.H1,
                    body);

            email.addSignature("Happy Syncing :)", fromPerson,
                    "p.s. Let us know what you think at " + SP_EMAIL_ADDRESS +
                            ". We'd love to hear your feedback!");
        }

        SVClient.sendEmail(SP_EMAIL_ADDRESS, fromPerson, to, null, subject, email.getTextEmail(),
                email.getHTMLEmail(), true, Category.FOLDERLESS_INVITE);

        EmailUtil.emailSPNotification(from + " invited " + to + (folderName != null ? " to " + folderName : " folderless"),
                "code " + signupCode);
    }


    public void sendFolderInvitationEmail(String from, String to, String fromPerson,
            @Nullable String folderName, @Nullable String note, String shareFolderCode)
            throws Exception
    {
        assert from != null;

        String subject = "Join my " + S.PRODUCT + " folder";

        Email email = new Email(subject);
        String url = SPParam.getWebDownloadLink(shareFolderCode, false);

        String nameAndEmail = fromPerson.isEmpty() ? from : fromPerson + " (" + from + ")";
        String body = "\n" + nameAndEmail + " has invited you to a shared " + S.PRODUCT + " folder"
                + (note != null ? (":\n\n" + note) : ".") + "\n\n"
                + "In the " + S.PRODUCT + " tray menu, click on \"Accept Invitation...\" and enter"
                + " the following code to accept the folder:\n\n"
                + shareFolderCode + "\n\n"
                + "You can download " + S.PRODUCT + " at " + url + ".";

        // If fromPerson is empty (user didn't set his name), use his email address instead
        String nameOrEmail = fromPerson.isEmpty() ? from : fromPerson;
        email.addSection(nameOrEmail + " wants to share " + Util.q(folderName) + " with you.",
                HEADER_SIZE.H1, body);

        email.addSignature("Best Regards,", "The " + S.PRODUCT + " Team", Email.DEFAULT_PS);

        SVClient.sendEmail(SP_EMAIL_ADDRESS,
                fromPerson,
                to,
                null,
                subject,
                email.getTextEmail(),
                email.getHTMLEmail(),
                true,
                Category.FOLDER_INVITE
                );

        EmailUtil.emailSPNotification(from + " shared " + folderName + " with " + to,
                "code " + shareFolderCode);
    }

}
