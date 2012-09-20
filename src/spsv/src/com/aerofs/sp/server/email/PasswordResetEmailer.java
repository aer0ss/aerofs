/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.lib.spsv.sendgrid.Sendgrid.Category;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;

import java.io.IOException;

import static com.aerofs.sp.server.SPSVParam.*;

public class PasswordResetEmailer
{
    public void sendPasswordResetEmail(String to, String reset_token)
            throws IOException

    {
        String subject = S.PRODUCT + " password request";

        Email email = new Email(subject);

        String url = S.PASSWORD_RESET_URL + "?" +
                "user_id=" + Util.urlEncode(to) +
                "&token=" + reset_token;
        String body = "\nForgot your password? It happens to the best of us.\n\nFollow this link " +
               "to reset your password:\n\n" + url + "\n\n" +
                "If you didn't request this email please ignore this message.";

        email.addSection(S.PRODUCT + " password request", HEADER_SIZE.H1, body);

        email.addSignature("Happy Syncing,", "The " + S.PRODUCT + " Team",
                Email.DEFAULT_PS);

        SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SP_EMAIL_NAME, to, null, subject,
                email.getTextEmail(), email.getHTMLEmail(), true, Category.PASSWORD_RESET);

        EmailUtil.emailSPNotification(to + " initiated a password reset ", "");
    }

    public void sendPasswordResetConfirmation(String to)
            throws IOException

    {
        String subject = S.PRODUCT + " password request confirmation";

        Email email = new Email(subject);

        String body = "\nCongrats! You've successfully created a new password " +
                "for your " + S.PRODUCT + " account!\n" +
                "You should now be able to continue syncing privately and securely.\n\n" +
                "If you didn't request a password reset, " +
                "please email " + SV.SUPPORT_EMAIL_ADDRESS + " right away.";

        email.addSection(S.PRODUCT + " password request was successful", HEADER_SIZE.H1, body);

        email.addSignature("Thank you for using " + S.PRODUCT + ",",
                "The " + S.PRODUCT + " Support Team",
                Email.DEFAULT_PS);

        SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SP_EMAIL_NAME, to, null, subject,
                email.getTextEmail(), email.getHTMLEmail(), true, Category.PASSWORD_RESET);

        EmailUtil.emailSPNotification(to + " completed a password reset ", "");
    }
}
