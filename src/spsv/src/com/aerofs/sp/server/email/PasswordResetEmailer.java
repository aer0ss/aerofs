/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.EmailSender;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sv.common.EmailCategory;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;

import javax.mail.MessagingException;
import java.io.IOException;

// TODO (WW) the pattern of this class is inconsistent with InvitationEmailer. Need refactoring.
public class PasswordResetEmailer
{
    public void sendPasswordResetEmail(UserID userId, String reset_token)
            throws IOException

    {
        String subject = L.brand() + " Password Request";

        Email email = new Email(subject, false ,null);

        String url = S.PASSWORD_RESET_URL + "?" +
                "user_id=" + Util.urlEncode(userId.getString()) +
                "&token=" + reset_token;
        String body = "\nForgot your password? It happens to the best of us.\n\nFollow this link " +
               "to reset your password:\n\n" + url + "\n\n" +
                "If you didn't request this email please ignore this message.";

        email.addSection(L.brand() + " Password Request", HEADER_SIZE.H1, body);
        email.addDefaultSignature();

        try {
            EmailSender.sendPublicEmail(WWW.SUPPORT_EMAIL_ADDRESS.get(), SPParam.EMAIL_FROM_NAME,
                    userId.getString(), null, subject, email.getTextEmail(), email.getHTMLEmail(),
                    EmailCategory.PASSWORD_RESET);
        } catch (MessagingException e) {
            throw new IOException(e);
        }

        EmailUtil.emailSPNotification(userId + " initiated a password reset ", "");
    }

    public void sendPasswordResetConfirmation(UserID userId)
            throws IOException

    {
        String subject = L.brand() + " Password Has Changed";

        Email email = new Email(subject, false, null);

        String body = "\n" +
                "You have changed the password for your " + L.brand() + " account.\n" +
                "\n" +
                "If you didn't change the password," +
                " please email " + WWW.SUPPORT_EMAIL_ADDRESS.get() + " immediately." +
                " We will take necessary steps to secure your account.";

        email.addSection(subject, HEADER_SIZE.H1, body);
        email.addDefaultSignature();

        try {
            EmailSender.sendPublicEmail(WWW.SUPPORT_EMAIL_ADDRESS.get(), SPParam.EMAIL_FROM_NAME,
                    userId.getString(), null, subject, email.getTextEmail(), email.getHTMLEmail(),
                    EmailCategory.PASSWORD_RESET);
        } catch (MessagingException e) {
            throw new IOException(e);
        }

        EmailUtil.emailSPNotification(userId + " completed a password reset ", "");
    }
}
