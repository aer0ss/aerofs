/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sp.server.lib.SPParam;

import javax.mail.MessagingException;
import java.io.IOException;

public class RequestToSignUpEmailer
{
    // See web/__init__.py for the reason we have different URLs for private and public deployment
    static String DASHBOARD_HOME = WWW.DASHBOARD_HOST_URL + "/";

    private static final AsyncEmailSender _emailSender = AsyncEmailSender.create();

    static String getSignUpLink(String signUpCode)
    {
        // N.B. the parameter key string must be identical to that in signup/views.py.
        return WWW.DASHBOARD_HOST_URL + "/signup?c=" + signUpCode;
    }

    public void sendRequestToSignUpEmail(String emailAddress, String signUpCode)
            throws IOException, MessagingException
    {
        String subject = "Complete your " + SPParam.BRAND + " sign up";
        String body = "\n" +
                "Please click this link to proceed signing up for " + SPParam.BRAND + ":\n" +
                "\n" +
                getSignUpLink(signUpCode) + "\n" +
                "\n" +
                "Simply ignore this email if you didn't request an " + SPParam.BRAND + " account.";

        Email email = new Email();
        email.addSection("You're almost ready to go!", body);
        email.addDefaultSignature();

        _emailSender.sendPublicEmailFromSupport(SPParam.EMAIL_FROM_NAME, emailAddress, null,
                subject, email.getTextEmail(), email.getHTMLEmail());
    }

    public void sendAlreadySignedUpEmail(String emailAddress)
            throws IOException, MessagingException
    {
        String subject = "Your " + SPParam.BRAND + " account";
        String body = "\n" +
                "It looks like you were trying to sign up for " + SPParam.BRAND + " again; but as" +
                " it turns out, you've already signed up!\n" +
                "\n" +
                "To log in, head over to " + DASHBOARD_HOME + " and type in your email address" +
                " and password.\n" +
                "\n" +
                "Forgot your password? No problem! You can reset it right here: " +
                WWW.PASSWORD_RESET_REQUEST_URL;

        Email email = new Email();
        email.addSection("You already have an " + SPParam.BRAND + " account", body);
        email.addDefaultSignature();

        _emailSender.sendPublicEmailFromSupport(SPParam.EMAIL_FROM_NAME, emailAddress, null,
                subject, email.getTextEmail(), email.getHTMLEmail());
    }
}
