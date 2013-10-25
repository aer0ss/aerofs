/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.email;

import com.aerofs.servlets.lib.SyncEmailSender;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sv.common.EmailCategory;

import javax.mail.MessagingException;
import java.io.IOException;

public class SmtpVerificationEmailer
{
    public static void sendSmtpVerificationEmail(String fromEmail, String toEmail, String code,
            String host, String port, String username, String password)
            throws IOException, MessagingException
    {
        String subject = "Your SMTP Verification Code";
        String body = "\n" +
                "Your SMTP verification code is:\n" +
                "\n" +
                code + "\n" +
                "\n" +
                "Please copy and paste this code into your browser as directed to continue the setup process.";

        Email email = new Email();
        email.addSection(subject, body);
        email.addDefaultSignature();

        // N.B. need to use this specific email sender constructor because the configuration
        // system is not up to date.
        SyncEmailSender emailSender = new SyncEmailSender(host, port, username, password);
        emailSender.sendPublicEmail(fromEmail, SPParam.EMAIL_FROM_NAME, toEmail, null, subject,
                email.getTextEmail(), email.getHTMLEmail(), EmailCategory.SMTP_VERIFICATION);
    }
}
