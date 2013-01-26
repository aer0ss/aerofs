/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.BaseParam.SV;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.labeling.L;
import com.aerofs.lib.Util;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sv.client.SVClient;
import com.aerofs.sv.common.EmailCategory;

import java.io.IOException;

public class RequestToSignUpEmailer
{
    static String getSignUpLinkWithFreePlan(String signUpCode)
    {
        // Redirect the user to the sign up page right after signing up.
        // N.B. the parameter key string must be identical to that in signup/views.py.
        return getSignUpLinkWithBusinessPlan(signUpCode) + "&next=" + Util.urlEncode("/install");
    }

    static String getSignUpLinkWithBusinessPlan(String signUpCode)
    {
        // The default page after the sign up page is billing. We set it as the default so users
        // wouldn't easily figure out by looking at the invitation URL that they can use AeroFS for
        // free immediately after signing up without paying. This can be used to bypass our Beta
        // invitation system. See src/web/moduels/signup/views.py.
        //
        // N.B. the parameter key string must be identical to that in signup/views.py.
        return SP.DASH_BOARD_BASE + "/signup?c=" + signUpCode;
    }

    public void sendRequestToSignUpWithBusinessPlanEmail(String emailAddress, String signUpCode)
            throws IOException
    {
        String subject = "Complete your " + L.PRODUCT + " sign up";
        Email email = new Email(subject);

        String body = "\n" +
                "Please click this link to go to proceed signing up " + L.PRODUCT + ":\n" +
                "\n" +
                getSignUpLinkWithBusinessPlan(signUpCode) + "\n" +
                "\n" +
                "Just ignore this email if you didn't request an account on " + L.PRODUCT + ".";

        email.addSection("You're almost ready to go!", HEADER_SIZE.H1, body);
        email.addDefaultSignature();

        try {
            SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SPParam.SP_EMAIL_NAME,
                    emailAddress, null, subject, email.getTextEmail(), email.getHTMLEmail(),
                    true, EmailCategory.REQUEST_TO_SIGN_UP_WITH_BUSINESS_PLAN);
        } catch (AbstractExWirable e) {
            throw new IOException(e);
        }

        EmailUtil.emailSPNotification(emailAddress +
                " request_to_sign_up_with_business_plan email.", "");
    }
}
