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
    static String getSignUpLink(String signUpCode)
    {
        // Redirect the user to the sign up page right after signing up.
        // N.B. the parameter key string must be identical to that in signup/views.py.
        // TODO (WW) use protobuf to share constants between Python and Java code?
        return SP.DASH_BOARD_BASE + "/signup?c=" + signUpCode + "&next=" + Util.urlEncode("/install");
    }

    static String getSignUpAndActivitateBusinessPlanLink(String signUpCode)
    {
        // The default page after the sign up page is billing. We set it as the default so users
        // wouldn't easily figure out by looking at the invitation URL that they can use AeroFS for
        // free immediately after signing up without paying. This can be used to bypass the
        // invitation system for Personal uses. See src/web/modules/signup/views.py.
        //
        // N.B. the parameter key string must be identical to that in signup/views.py.
        // TODO (WW) use protobuf to share constants between Python and Java code?
        return SP.DASH_BOARD_BASE + "/signup?c=" + signUpCode;
    }

    static String getActivateBusinessPlanLink()
    {
        // TODO (WW) use protobuf to share constants between Python and Java code?
        return SP.DASH_BOARD_BASE + "/business/activate";
    }

    public void sendRequestToSignUpAndActivateBusinessPlan(String emailAddress, String signUpCode)
            throws IOException
    {
        send(emailAddress, "Complete your " + L.PRODUCT + " sign up",
                getSignUpAndActivitateBusinessPlanLink(signUpCode),
                EmailCategory.REQUEST_TO_SIGN_UP_AND_ACTIVATE_BUSINESS_PLAN);
    }

    public void sendRequestToActivateBusinessPlan(String emailAddress)
            throws IOException
    {
        send(emailAddress, "Start your " + L.PRODUCT + " for Business free trial",
                getActivateBusinessPlanLink(),
                EmailCategory.REQUEST_TO_ACTIVATE_BUSINESS_PLAN);
    }

    private void send(String emailAddress, String subject, String url, EmailCategory category)
            throws IOException
    {
        Email email = new Email(subject);
        String body = "\n" +
                "Please click this link to proceed signing up " + L.PRODUCT + " for Business:\n" +
                "\n" +
                url + "\n" +
                "\n" +
                "Just ignore this email if you didn't request " + L.PRODUCT + " for Business.";

        email.addSection("You're almost ready to go!", HEADER_SIZE.H1, body);
        email.addDefaultSignature();

        try {
            SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SPParam.SP_EMAIL_NAME, emailAddress, null,
                    subject, email.getTextEmail(), email.getHTMLEmail(), true,
                    category);
        } catch (AbstractExWirable e) {
            throw new IOException(e);
        }
    }
}
