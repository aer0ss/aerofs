/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
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
            SVClient.sendEmail(WWW.SUPPORT_EMAIL_ADDRESS.get(), SPParam.SP_EMAIL_NAME, userId.getString(),
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
        String subject = L.brand() + " Password Has Changed";

        Email email = new Email(subject, false, null);

        String body = "\n" +
                "You have changed the password for your " + L.brand() + " account.\n" +
                "\n" +
                "If you didn't change the password," +
                " please email " + WWW.SUPPORT_EMAIL_ADDRESS + " immediately." +
                " We will take necessary steps to secure your account.";

        email.addSection(subject, HEADER_SIZE.H1, body);
        email.addDefaultSignature();

        try {
            SVClient.sendEmail(WWW.SUPPORT_EMAIL_ADDRESS.get(), SPParam.SP_EMAIL_NAME, userId.getString(),
                    null, subject, email.getTextEmail(), email.getHTMLEmail(), true,
                    EmailCategory.PASSWORD_RESET);
        } catch (AbstractExWirable e) {
            throw new IOException(e);
        }

        EmailUtil.emailSPNotification(userId + " completed a password reset ", "");
    }
}
