/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;

import java.io.UnsupportedEncodingException;

import static com.aerofs.sp.server.SPSVParam.SP_EMAIL_ADDRESS;


public class PasswordResetEmailer
{
    public void sendPasswordResetEmail(String to, String reset_token)
            throws MessagingException, UnsupportedEncodingException
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
                EmailUtil.DEFAULT_PS);

        MimeMessage msg = EmailUtil.composeEmail(SP_EMAIL_ADDRESS,null,to,null,
                subject,null);

        MimeMultipart multiPart = EmailUtil.createMultipartEmail(email);

        msg.setContent(multiPart);

        EmailUtil.sendEmail(msg,true);
    }

    public void sendPasswordResetConfirmation(String to)
            throws MessagingException, UnsupportedEncodingException
    {
        String subject = S.PRODUCT + " password request confirmation";

        Email email = new Email(subject);

        String body = "\nCongrats! You've successfully created a new password " +
                "for your " + S.PRODUCT + " account!\n" +
                "You should now be able to continue syncing privately and securely.\n\n" +
                "If you didn't request a password reset, " +
                "please email " + SP_EMAIL_ADDRESS + " right away.";

        email.addSection(S.PRODUCT + " password request was successful", HEADER_SIZE.H1, body);

        email.addSignature("Thank you for using " + S.PRODUCT + ",",
                "The " + S.PRODUCT + " Support Team",
                EmailUtil.DEFAULT_PS);

        MimeMessage msg = EmailUtil.composeEmail(SP_EMAIL_ADDRESS,null,to,null,
                subject,null);

        MimeMultipart multiPart = EmailUtil.createMultipartEmail(email);

        msg.setContent(multiPart);

        EmailUtil.sendEmail(msg,true);
    }
}
