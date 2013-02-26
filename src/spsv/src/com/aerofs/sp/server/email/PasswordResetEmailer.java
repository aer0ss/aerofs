/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.base.BaseParam.SV;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sv.client.SVClient;
import com.aerofs.sv.common.EmailCategory;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;

import java.io.IOException;

// TODO (WW) the pattern of this class is inconsistent with InvitationEmailer. Need refactoring
// and/or merging.
public class PasswordResetEmailer
{
    public void sendPasswordResetEmail(UserID userId, String reset_token)
            throws IOException

    {
        String subject = L.PRODUCT + " Password Request";

        Email email = new Email(subject, false ,null);

        String url = S.PASSWORD_RESET_URL + "?" +
                "user_id=" + Util.urlEncode(userId.getString()) +
                "&token=" + reset_token;
        String body = "\nForgot your password? It happens to the best of us.\n\nFollow this link " +
               "to reset your password:\n\n" + url + "\n\n" +
                "If you didn't request this email please ignore this message.";

        email.addSection(L.PRODUCT + " Password Request", HEADER_SIZE.H1, body);
        email.addDefaultSignature();

        try {
            SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SPParam.SP_EMAIL_NAME, userId.getString(),
                    null, subject, email.getTextEmail(), email.getHTMLEmail(), true,
                    EmailCategory.PASSWORD_RESET);
        } catch (AbstractExWirable e) {
            throw new IOException(e);
        }

        EmailUtil.emailSPNotification(userId + " initiated a password reset ", "");
    }

    public void sendPasswordResetConfirmation(UserID userId)
            throws IOException

    {
        String subject = L.PRODUCT + " Password Reset Confirmation";

        Email email = new Email(subject, false, null);

        String body = "\n" +
                "You've successfully created a new password for your " + L.PRODUCT + " account.\n" +
                "\n" +
                "If you didn't request a password reset, " +
                "please email " + SV.SUPPORT_EMAIL_ADDRESS + " right away.";

        email.addSection(L.PRODUCT + " Password Reset was Successful", HEADER_SIZE.H1, body);
        email.addDefaultSignature();

        try {
            SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SPParam.SP_EMAIL_NAME, userId.getString(),
                    null, subject, email.getTextEmail(), email.getHTMLEmail(), true,
                    EmailCategory.PASSWORD_RESET);
        } catch (AbstractExWirable e) {
            throw new IOException(e);
        }

        EmailUtil.emailSPNotification(userId + " completed a password reset ", "");
    }
}
