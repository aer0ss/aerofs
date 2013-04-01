/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.BaseParam.SV;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.labeling.L;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sv.client.SVClient;
import com.aerofs.sv.common.EmailCategory;

import java.io.IOException;

public class RequestToSignUpEmailer
{
    static String getSignUpLink(String signUpCode)
    {
        // N.B. the parameter key string must be identical to that in signup/views.py.
        return SP.DASH_BOARD_BASE + "/signup?c=" + signUpCode;
    }

    public void sendRequestToSignUp(String emailAddress, String signUpCode)
            throws IOException
    {
        Email email = new Email("Complete your " + L.PRODUCT + " sign up");
        String body = "\n" +
                "Please click this link to proceed signing up " + L.PRODUCT + ":\n" +
                "\n" +
                getSignUpLink(signUpCode) + "\n" +
                "\n" +
                "Simply ignore this email if you didn't request an " + L.PRODUCT + " account.";

        email.addSection("You're almost ready to go!", HEADER_SIZE.H1, body);
        email.addDefaultSignature();

        try {
            SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SPParam.SP_EMAIL_NAME, emailAddress, null,
                    "Complete your " + L.PRODUCT + " sign up", email.getTextEmail(),
                    email.getHTMLEmail(), true,
                    EmailCategory.REQUEST_TO_SIGN_UP);
        } catch (AbstractExWirable e) {
            throw new IOException(e);
        }
    }

}
