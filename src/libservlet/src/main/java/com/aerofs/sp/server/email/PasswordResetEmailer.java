/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.ids.UserID;
import com.aerofs.lib.Util;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sp.server.lib.SPParam;

import javax.mail.MessagingException;
import java.io.IOException;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class PasswordResetEmailer
{
    private static final AsyncEmailSender _emailSender = AsyncEmailSender.create();
    private static final String _passwordResetUrl = getStringProperty("base.www.password_reset_url");

    public void sendPasswordResetEmail(UserID userId, String resetToken)
            throws IOException, MessagingException
    {
        String subject = SPParam.BRAND + " password request";
        String url = _passwordResetUrl + "?" +
                "user_id=" + Util.urlEncode(userId.getString()) +
                "&token=" + resetToken;
        String body = "\nForgot your password? It happens to the best of us.\n\nFollow this link " +
               "to reset your password:\n\n" + url + "\n\n" +
                "If you didn't request this email please ignore this message.";

        sendPublicEmail(userId, subject, body);
    }

    public void sendPasswordResetEmailToExternallyManagedAccount(UserID userId)
            throws IOException, MessagingException
    {
        String subject = SPParam.BRAND + " password request";

        String body = "\nWe are unable to reset your password at this time because\n" +
                "your account is being managed by an external identity service.\n" +
                "Please contact " + WWW.SUPPORT_EMAIL_ADDRESS + " for assistance with resetting your credential.\n\n" +
                "If you didn't request this email please ignore this message.";
        sendPublicEmail(userId, subject, body);
    }

    public void sendPasswordResetConfirmation(UserID userId)
            throws IOException, MessagingException
    {
        String subject = SPParam.BRAND + " password has changed";
        String body = "\n" +
                "You have changed the password for your " + SPParam.BRAND + " account.\n" +
                "\n" +
                "If you didn't change the password," +
                " please email " + WWW.SUPPORT_EMAIL_ADDRESS + " immediately." +
                " We will take necessary steps to secure your account.";

        sendPublicEmail(userId, subject, body);
    }

    /**
     * Notify a user that the password was changed - by them or by their admin.
     */
    public void sendPasswordChangeNotification(UserID userId)
            throws IOException, MessagingException
    {
        String subject = SPParam.BRAND + " password has changed";
        String body = "\n" +
                "You, or an administrator in your organization, has changed the password for your "
                + SPParam.BRAND + " account.\n\n" +
                "If you didn't expect this password change," +
                " please contact " + WWW.SUPPORT_EMAIL_ADDRESS + " immediately.";

        sendPublicEmail(userId, subject, body);
    }

    /**
     * Notify a user that the password was revoked - this includes a reset token.
     */
    public void sendPasswordRevokeNotification(UserID userId, String resetToken)
            throws IOException, MessagingException
    {
        String subject = SPParam.BRAND + " password request";
        String url = _passwordResetUrl + "?" +
                "user_id=" + Util.urlEncode(userId.getString()) +
                "&token=" + resetToken;
        String body = "\nAn administrator in your organization has changed the password for your "
                + SPParam.BRAND + " account.\n\nFollow this link " +
                "to set a new password:\n\n" + url + "\n\n" +
                "If you didn't expect this password change," +
                " please contact " + WWW.SUPPORT_EMAIL_ADDRESS + " immediately.";

        sendPublicEmail(userId, subject, body);
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
