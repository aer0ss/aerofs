/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.BaseParam.SV;
import com.aerofs.labeling.L;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.lib.Util;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sv.client.SVClient;
import com.aerofs.sv.common.EmailCategory;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;

import java.io.IOException;

public class DeviceRegistrationEmailer
{
    public void sendTeamServerDeviceCertifiedEmail(String emailAddress, String firstName,
            String osFamily, String deviceName)
            throws IOException
    {
        sendDeviceCertifiedEmailImpl(L.PRODUCT + " Team Server", emailAddress, firstName,
                osFamily, deviceName);
    }

    public void sendDeviceCertifiedEmail(String emailAddress, String firstName,
            String osFamily, String deviceName)
            throws IOException
    {
        sendDeviceCertifiedEmailImpl(L.PRODUCT, emailAddress, firstName, osFamily, deviceName);
    }

    public void sendDeviceCertifiedEmailImpl(String product, String emailAddress, String firstName,
            String osFamily, String deviceName)
            throws IOException
    {
        String subject = product + " Installed on Your Device " + Util.quote(deviceName);
        Email email = new Email(subject);

        // N.B. the URI string must be identical to that in devices/__init__.py.
        String url = SP.DASH_BOARD_BASE + "/devices";

        String body = "\n" +
                "Hi " + firstName + ",\n" +
                "\n" +
                product + " has recently been installed on a new " + osFamily + " device named " +
                Util.quote(deviceName) + ".\n" +
                "\n" +
                "If this device does not belong to you, please email us at " +
                SV.SUPPORT_EMAIL_ADDRESS +
                " immediately and we will take the necessary steps to secure your account.\n" +
                "\n" +
                "You can manage your devices at " + url + ".";

        email.addSection(subject, HEADER_SIZE.H1, body);
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
