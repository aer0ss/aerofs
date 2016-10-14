/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sp.server.lib.SPParam;
import com.google.common.base.Joiner;

import javax.mail.MessagingException;
import java.io.IOException;

public class TwoFactorEmailer
{
    private static final AsyncEmailSender _emailSender = AsyncEmailSender.create();

    public void sendTwoFactorEnabledEmail(String emailAddress, String firstName)
        throws IOException, MessagingException
    {
        String subject = "Two-factor authentication enabled";
        String body = getEnabledBody(emailAddress, firstName);
        Email email = new Email();
        email.addSection(subject, body);
        email.addDefaultSignature();

        _emailSender.sendPublicEmailFromSupport(SPParam.EMAIL_FROM_NAME, emailAddress, null,
                subject, email.getTextEmail(), email.getHTMLEmail());
    }

    public void sendTwoFactorDisabledEmail(String emailAddress, String firstName)
        throws IOException, MessagingException
    {
        String subject = "Two-factor authentication disabled";
        String body = getDisabledBody(emailAddress, firstName);
        Email email = new Email();
        email.addSection(subject, body);
        email.addDefaultSignature();

        _emailSender.sendPublicEmailFromSupport(SPParam.EMAIL_FROM_NAME, emailAddress, null,
                subject, email.getTextEmail(), email.getHTMLEmail());
    }

    // I wish I had a template language...
    private static String getEnabledBody(String emailAddress, String firstName)
    {
        String salutation = getSalutation(firstName);

        String preamble =
                "You just enabled two-factor authentication on your " + emailAddress +
                " AeroFS account.";

        // Technically, org admins can disable your second factor, but we should teach people that
        // this is a Thing Which Should Be Taken Seriously.
        String request =
                "Please, take a minute to download and print your backup codes from " +
                getSettingsURL();
        String admonition =
                "If you lose your phone, delete your authenticator application, or lose your " +
                "secret or access to your device in any way, backup codes are the ONLY way to " +
                "access your account again.";

        String disabling =
                "To disable two-factor authentication, visit " + getSettingsURL();

        String moreinfo =
                "More information about two-factor authentication can be found at " +
                "https://support.aerofs.com/hc/en-us/articles/202775400";

        String questions =
                "Please email " + WWW.SUPPORT_EMAIL_ADDRESS + " with any questions.";

        return Joiner.on("\n\n").join(salutation, preamble, request, admonition, disabling,
                questions);
    }

    private static String getDisabledBody(String emailAddress, String firstName)
    {
        String salutation = getSalutation(firstName);
        String observation =
                "It looks like you just disabled two-factor authentication on your " +
                emailAddress + " AeroFS account.";

        String reenabling =
                "If you want to reenable two-factor authentication, visit " +
                getSettingsURL();

        return Joiner.on("\n\n").join(salutation, observation, reenabling);
    }

    private static String getSalutation(String firstName)
    {
        return "Hi " + firstName + "!";
    }

    private static String getSettingsURL()
    {
        return WWW.DASHBOARD_HOST_URL + "/settings/two_factor_authentication";
    }
}
