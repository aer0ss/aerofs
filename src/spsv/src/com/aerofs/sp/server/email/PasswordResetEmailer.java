/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;

import java.io.UnsupportedEncodingException;

import static com.aerofs.sp.server.SPSVParam.SP_EMAIL_ADDRESS;
import static com.aerofs.sp.server.email.EmailUtil.CHARSET;


public class PasswordResetEmailer
{
    public void sendPasswordResetEmail(String to, String reset_token)
            throws MessagingException, UnsupportedEncodingException
    {
        String subject = S.PRODUCT + " password request";

        Email email = new Email(subject);

        String url = "https://www.aerofs.com/password_reset?" +
                "user_id=" + Util.urlEncode(to) +
                "&token=" + reset_token;
        String body = "\nForgot your password? It happens to the best of us.\n\nFollow this link " +
               "to reset your password:\n\n" + url + "\n\n" +
                "If you didn't request this email please ignore this message.";

        email.addSection(S.PRODUCT + " password request", HEADER_SIZE.H1, body);

        email.addSignature("Happy Syncing,", "The " + S.PRODUCT + " Team",
                "Have questions or comments? Email us at " + SP_EMAIL_ADDRESS);

        MimeMultipart multiPart = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();
        MimeBodyPart htmlPart = new MimeBodyPart();

        textPart.setContent(email.getTextEmail(), "text/plain; charset=\"" + CHARSET +
                "\"");
        htmlPart.setContent(email.getHTMLEmail(), "text/html; charset=\"" + CHARSET + "\"");

        multiPart.addBodyPart(textPart);
        multiPart.addBodyPart(htmlPart);

        MimeMessage msg = EmailUtil.composeEmail(SP_EMAIL_ADDRESS,null,to,null,
                subject,null);

        msg.setContent(multiPart);

        EmailUtil.sendEmail(msg,true);
    }

    public void sendPasswordResetConfirmation(String to)
            throws MessagingException, UnsupportedEncodingException
    {
        String subject = S.PRODUCT + " password request confirmation";

        Email email = new Email(subject);

        String body = "\nYour password has been successfully reset.";

        email.addSection(S.PRODUCT + " password request was successful", HEADER_SIZE.H1, body);

        email.addSignature("Happy Syncing,", "The " + S.PRODUCT + " Team",
                "Have questions or comments? Email us at " + SP_EMAIL_ADDRESS);

        MimeMultipart multiPart = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();
        MimeBodyPart htmlPart = new MimeBodyPart();

        textPart.setContent(email.getTextEmail(), "text/plain; charset=\"" + CHARSET +
                "\"");
        htmlPart.setContent(email.getHTMLEmail(), "text/html; charset=\"" + CHARSET + "\"");

        multiPart.addBodyPart(textPart);
        multiPart.addBodyPart(htmlPart);

        MimeMessage msg = EmailUtil.composeEmail(SP_EMAIL_ADDRESS,null,to,null,
                subject,null);

        msg.setContent(multiPart);

        EmailUtil.sendEmail(msg,true);
    }

}
