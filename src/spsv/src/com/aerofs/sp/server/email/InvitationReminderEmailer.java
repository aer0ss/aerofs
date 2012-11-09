/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import java.io.IOException;
import java.util.concurrent.Callable;

import com.aerofs.lib.Param.SP;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.S;
import com.aerofs.lib.ex.ExEmailSendingFailed;
import com.aerofs.sv.client.SVClient;
import com.aerofs.sv.common.EmailCategory;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;
import com.aerofs.sp.server.lib.SPParam;

public class InvitationReminderEmailer {

    public static class Factory {

        public InvitationReminderEmailer createReminderEmail(final String fromEmail,
                final String fromName,
                final String to, String signupCode, String unsubscribeId)
                        throws IOException
        {
            String url = SPParam.getWebDownloadLink(signupCode, false);

            final String subject = "Reminder: You've recently been invited to try AeroFS!";

            final String body = "\nHi there!\n\n" +
                    "You've recently been invited to try out " + S.PRODUCT +
                    " (" + SP.WEB_BASE + ") \n\n " +
                    "We realize you might be quite busy and may have missed our invitation email " +
                    "so we want to remind you that your invitation is still " +
                    "waiting!.\n\n" +
                    "As a quick refresher: " + S.PRODUCT + " allows you to sync, share, and " +
                    "collaborate on files privately and securely.\n Any data that you put inside your "+
                    S.PRODUCT + " will be synced *only* with your personal devices, and anyone you " +
                    "invite to share with you.\n\n Please keep in mind that " + S.PRODUCT +
                    " is still in beta! We release updates regularly and appreciate any and all " +
                    "feedback.\n\n You can download " + S.PRODUCT + " at:\n\n" + url + "\n\n" +
                    "And when prompted, enter the following invitation code:\n\n" + signupCode;


            final Email email = new Email(subject, true, unsubscribeId);

            email.addSection("Reminder: You're invited to " + S.PRODUCT +"!", HEADER_SIZE.H1, body);

            email.addSignature("Happy Syncing :)", fromName,
                    "p.s. Let us know what you think at " + SV.SUPPORT_EMAIL_ADDRESS +
                    ". We'd love to hear your feedback!");

            return new InvitationReminderEmailer(new Callable<Void>()
            {
                @Override
                public Void call()
                        throws Exception
                {
                    SVClient.sendEmail(fromEmail, fromName, to, null, subject, email.getTextEmail(),
                            email.getHTMLEmail(), true, EmailCategory.AEROFS_INVITATION_REMINDER);

                    return null;
                }
            });
        }
    }

    private final Callable<Void> _c;

    public InvitationReminderEmailer()
    {
        _c = null;
    }

    private InvitationReminderEmailer(Callable<Void> c)
    {
        _c = c;
    }

    public void send() throws Exception
    {
        if (_c != null) _c.call();
    }

    public static void sendAll(Iterable<InvitationReminderEmailer> emails) throws
            ExEmailSendingFailed
    {
        try {
            for (InvitationReminderEmailer email : emails) email.send();
        } catch (Exception e) {
            throw new ExEmailSendingFailed(e);
        }
    }

}
