/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.lib.Param.SP;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExEmailSendingFailed;
import com.aerofs.sv.client.SVClient;
import com.aerofs.sv.common.EmailCategory;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;
import com.aerofs.sp.server.lib.SPParam;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
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
                    ? "Join my " + S.PRODUCT + " folder"
                    : "Invitation to " + S.PRODUCT + " (beta)";

            String intro = S.PRODUCT + " is a file syncing, sharing, and collaboration tool that " +
                "lets you sync files without using cloud servers. You can learn more about it at " +
                "" + SP.WEB_BASE + ".";

            String body;
            final Email email = new Email(subject, false, null);

            if (inviter != null) {
                String nameAndEmail = inviterName.isEmpty() ? inviter : inviterName + " (" +
                        inviter + ")";
                body = "\n" + nameAndEmail + " has invited you to "
                    + (folderName != null ? "a shared " + S.PRODUCT + " folder" : S.PRODUCT)
                    + (note != null ? ":\n\n" + note : ".") + "\n\n"
                    + intro + "\n\n" +
                    "Download " + S.PRODUCT + " at:\n\n" + url + "\n\n" +
                    "And when prompted, enter the following invitation code:\n\n" + signupCode;

                // If fromPerson is empty (user didn't set his name), use his email address instead
                String nameOrEmail = inviterName.isEmpty() ? inviter : inviterName;
                email.addSection(nameOrEmail + " invited you to " + S.PRODUCT + "!",
                        HEADER_SIZE.H1, body);

                email.addSignature("Best Regards,", "The " + S.PRODUCT + " Team",
                        "Have questions or comments? Email us at " + SV.SUPPORT_EMAIL_ADDRESS);
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
                @Nullable final String note, final String shareFolderCode)
                throws IOException
        {
            final String subject = "Join my " + S.PRODUCT + " folder";

            final Email email = new Email(subject, false, null);

            String url = SPParam.getWebDownloadLink(shareFolderCode);

            String nameAndEmail = fromPerson.isEmpty() ? from : fromPerson + " (" + from + ")";
            String body = "\n" + nameAndEmail + " has invited you to a shared " + S.PRODUCT
                    + " folder" + (note != null ? (":\n\n" + note) : ".") + "\n\n"
                    + "In the " + S.PRODUCT + " tray menu, click on \"Accept Invitation...\" and "
                    + "enter the following code to accept the folder:\n\n"
                    + shareFolderCode + "\n\n"
                    + "You can download " + S.PRODUCT + " at " + url + ".";

            // If fromPerson is empty (user didn't set his name), use his email address instead
            String nameOrEmail = fromPerson.isEmpty() ? from : fromPerson;
            email.addSection(nameOrEmail + " wants to share " + Util.quote(folderName) + " with you.",
                    HEADER_SIZE.H1, body);

            email.addSignature("Best Regards,", "The " + S.PRODUCT + " Team", Email.DEFAULT_PS);

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
                            "code " + shareFolderCode);
                    return null;
                }
            });
        }
    }

    private final Callable<Void> _c;

    private InvitationEmailer(Callable<Void> c)
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
