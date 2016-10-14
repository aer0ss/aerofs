/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sp.server.lib.SPParam;

public class InvitationReminderEmailer
{
    private static final AsyncEmailSender _emailSender = AsyncEmailSender.create();

    public void send(String fromName, final String to, String signUpCode, String unsubscribeId)
            throws Exception
    {
        String url = RequestToSignUpEmailer.getSignUpLink(signUpCode);

        String subject = "Reminder: You've recently been invited to try AeroFS!";

        String body = "\n" +
                "Hi there!\n" +
                "\n" +
                "You've recently been invited to try out " + SPParam.BRAND +
                // Whitespace around URL needed for autolinker
                " ( " + WWW.MARKETING_HOST_URL + " ) \n" +
                "\n " +
                "We realize you might be quite busy and may have missed our invitation email" +
                " so we want to remind you that your invitation is still waiting!\n" +
                "\n" +
                "As a quick refresher: " + SPParam.BRAND + " allows you to sync, share, and" +
                " collaborate on files privately and securely.\n" +
                "\n" +
                "Any data that you put inside your " + SPParam.BRAND + " folder will be synced *only*" +
                " with your personal devices, and anyone you invite to share with you.\n" +
                "\n" +
                "You can download " + SPParam.BRAND + " at:\n" +
                "\n" +
                url;

        Email email = new Email(true, unsubscribeId);

        email.addSection("Reminder: You're invited to " + SPParam.BRAND +"!", body);
        email.addDefaultSignature();

        _emailSender.sendPublicEmailFromSupport(fromName, to, null, subject, email.getTextEmail(),
                email.getHTMLEmail());
    }
}
