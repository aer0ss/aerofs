/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import java.io.IOException;
import java.util.concurrent.Callable;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.labeling.L;
import com.aerofs.sv.client.SVClient;
import com.aerofs.sv.common.EmailCategory;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;

public class InvitationReminderEmailer
{
    public static class Factory {

        public InvitationReminderEmailer createReminderEmail(final String from,
                final String fromName,
                final String to, String signUpCode, String unsubscribeId)
                        throws IOException
        {
            String url = RequestToSignUpEmailer.getSignUpLink(signUpCode);

            final String subject = "Reminder: You've recently been invited to try AeroFS!";

            final String body = "\n" +
                    "Hi there!\n" +
                    "\n" +
                    "You've recently been invited to try out " + L.product() +
                    " (" + WWW.MARKETING_HOST_URL + ") \n" +
                    "\n " +
                    "We realize you might be quite busy and may have missed our invitation email" +
                    " so we want to remind you that your invitation is still waiting!\n" +
                    "\n" +
                    "As a quick refresher: " + L.product() + " allows you to sync, share, and" +
                    " collaborate on files privately and securely.\n" +
                    "\n" +
                    "Any data that you put inside your " + L.product() + " will be synced *only*" +
                    " with your personal devices, and anyone you invite to share with you.\n" +
                    "\n" +
                    "You can download " + L.product() + " at:\n" +
                    "\n" +
                    url;


            final Email email = new Email(subject, true, unsubscribeId);

            email.addSection("Reminder: You're invited to " + L.product() +"!", HEADER_SIZE.H1, body);
            email.addDefaultSignature();

            return new InvitationReminderEmailer(new Callable<Void>()
            {
                @Override
                public Void call()
                        throws Exception
                {
                    SVClient.sendEmail(from, fromName, to, null, subject,
                            email.getTextEmail(),
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
}
