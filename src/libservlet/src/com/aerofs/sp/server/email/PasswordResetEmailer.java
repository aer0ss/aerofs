/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.id.UserID;
import com.aerofs.labeling.L;
import com.aerofs.lib.Util;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sp.server.lib.SPParam;

import javax.mail.MessagingException;
import java.io.IOException;

public class PasswordResetEmailer
{
    private static final AsyncEmailSender _emailSender = AsyncEmailSender.create();

    public void sendPasswordResetEmail(UserID userId, String resetToken)
            throws IOException, MessagingException
    {
        String subject = L.brand() + " password request";
        String url = WWW.PASSWORD_RESET_URL + "?" +
                "user_id=" + Util.urlEncode(userId.getString()) +
                "&token=" + resetToken;
        String body = "\nForgot your password? It happens to the best of us.\n\nFollow this link " +
               "to reset your password:\n\n" + url + "\n\n" +
                "If you didn't request this email please ignore this message.";

        sendPublicEmail(userId, subject, body);
        EmailUtil.emailInternalNotification(userId + " initiated a password reset ", "");
    }

    public void sendPasswordResetConfirmation(UserID userId)
            throws IOException, MessagingException
    {
        String subject = L.brand() + " password has changed";
        String body = "\n" +
                "You have changed the password for your " + L.brand() + " account.\n" +
                "\n" +
                "If you didn't change the password," +
                " please email " + WWW.SUPPORT_EMAIL_ADDRESS + " immediately." +
                " We will take necessary steps to secure your account.";

        sendPublicEmail(userId, subject, body);
        EmailUtil.emailInternalNotification(userId + " completed a password reset ", "");
    }

    /**
     * Notify a user that the password was changed - by them or by their admin.
     */
    public void sendPasswordChangeNotification(UserID userId)
            throws IOException, MessagingException
    {
        String subject = L.brand() + " password has changed";
        String body = "\n" +
                "You, or an administrator in your organization, has changed the password for your "
                + L.brand() + " account.\n\n" +
                "If you didn't expect this password change," +
                " please contact " + WWW.SUPPORT_EMAIL_ADDRESS + " immediately.";

        sendPublicEmail(userId, subject, body);

        EmailUtil.emailInternalNotification(userId + " password changed by admin action ", "");
    }

    /**
     * Notify a user that the password was revoked - this includes a reset token.
     */
    public void sendPasswordRevokeNotification(UserID userId, String resetToken)
            throws IOException, MessagingException
    {
        String subject = L.brand() + " password request";
        String url = WWW.PASSWORD_RESET_URL + "?" +
                "user_id=" + Util.urlEncode(userId.getString()) +
                "&token=" + resetToken;
        String body = "\nAn administrator in your organization has changed the password for your "
                + L.brand() + " account.\n\nFollow this link " +
                "to set a new password:\n\n" + url + "\n\n" +
                "If you didn't expect this password change," +
                " please contact " + WWW.SUPPORT_EMAIL_ADDRESS + " immediately.";

        sendPublicEmail(userId, subject, body);
        EmailUtil.emailInternalNotification(userId + " password was revoked ", "");
    }

    private void sendPublicEmail(UserID target, String subject, String body)
            throws IOException, MessagingException
    {
        Email email = new Email();
        email.addSection(subject, body);
        email.addDefaultSignature();

        _emailSender.sendPublicEmailFromSupport(SPParam.EMAIL_FROM_NAME, target.getString(), null,
                subject, email.getTextEmail(), email.getHTMLEmail());
    }
}
