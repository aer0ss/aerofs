/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.SV;
import com.aerofs.labeling.L;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sv.client.SVClient;
import com.aerofs.sv.common.EmailCategory;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;

import java.io.IOException;

public class DeviceCertifiedEmailer
{
    public void sendTeamServerDeviceCertifiedEmail(String emailAddress, String firstName)
            throws IOException
    {
        String subject = L.PRODUCT + " Device Installed";
        Email email = new Email(subject);

        String body = "\n" +
                "Hi " + firstName + ",\n" +
                "\n" +
                // TODO (WW) create ILabeling.multiuserProductName() to replace the hard coded
                // "Team Server".
                "This email is to notify you that " + L.PRODUCT + " Team Server has recently" +
                " been installed on a new device using your account.\n" +
                "\n" +
                "If you did not authorize this, please email us at " +
                SV.SUPPORT_EMAIL_ADDRESS +
                " and we will take the necessary steps to secure your account.";

        email.addSection(L.PRODUCT + " device installed", HEADER_SIZE.H1, body);
        email.addDefaultSignature();

        try {
            SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SPParam.SP_EMAIL_NAME,
                    emailAddress, null, subject, email.getTextEmail(), email.getHTMLEmail(),
                    true, EmailCategory.DEVICE_CERTIFIED);
        } catch (AbstractExWirable e) {
            throw new IOException(e);
        }

        EmailUtil.emailSPNotification(emailAddress + " team server device certified email.", "");
    }

    public void sendDeviceCertifiedEmail(String emailAddress, String firstName)
            throws IOException
    {
        String subject = L.PRODUCT + " Device Installed";
        Email email = new Email(subject);

        String body = "\n" +
                "Hi " + firstName + ",\n" +
                "\n" +
                "This email is to notify you that " + L.PRODUCT + " has recently been installed" +
                " on a new device using your account.\n" +
                "\n" +
                "If this device does not belong to you, please email us at " +
                SV.SUPPORT_EMAIL_ADDRESS +
                " and we will take the necessary steps to secure your account.";

        email.addSection(L.PRODUCT + " Device Installed", HEADER_SIZE.H1, body);
        email.addDefaultSignature();

        try {
            SVClient.sendEmail(SV.SUPPORT_EMAIL_ADDRESS, SPParam.SP_EMAIL_NAME,
                    emailAddress, null, subject, email.getTextEmail(), email.getHTMLEmail(),
                    true, EmailCategory.DEVICE_CERTIFIED);
        } catch (AbstractExWirable e) {
            throw new IOException(e);
        }

        EmailUtil.emailSPNotification(emailAddress + " device certified email.", "");
    }
}
