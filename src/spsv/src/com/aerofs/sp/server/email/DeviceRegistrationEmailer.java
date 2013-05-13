/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.id.DID;
import com.aerofs.labeling.L;
import com.aerofs.lib.Util;
import com.aerofs.servlets.lib.EmailSender;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sv.common.EmailCategory;
import com.aerofs.sp.server.email.IEmail.HEADER_SIZE;

import javax.mail.MessagingException;
import java.io.IOException;

public class DeviceRegistrationEmailer
{
    public void sendTeamServerDeviceCertifiedEmail(String emailAddress, String firstName,
            String osFamily, String deviceName, DID did)
            throws IOException
    {
        // N.B. the URI string must be identical to that in devices/__init__.py.
        sendDeviceCertifiedEmailImpl(L.brand() + " Team Server", emailAddress, firstName, osFamily,
                deviceName, "Team Servers at " + WWW.TEAM_SERVER_DEVICES_URL.get(), did);
    }

    public void sendDeviceCertifiedEmail(String emailAddress, String firstName,
            String osFamily, String deviceName, DID did)
            throws IOException
    {
        // N.B. the URI string must be identical to that in devices/__init__.py.
        sendDeviceCertifiedEmailImpl(L.brand(), emailAddress, firstName, osFamily, deviceName,
                "your devices at " + WWW.DEVICES_URL.get(), did);
    }

    public void sendDeviceCertifiedEmailImpl(String product, String emailAddress, String firstName,
            String osFamily, String deviceName, String manageDeviceStringAndURL, DID did)
            throws IOException
    {
        String subject = product + " Installed on Your Device " + Util.quote(deviceName);
        Email email = new Email(subject);

        String body = "\n" +
                "Hi " + firstName + ",\n" +
                "\n" +
                product + " has recently been installed on a new " + osFamily + " device named " +
                Util.quote(deviceName) + ".\n" +
                "\n" +
                "If this device does not belong to you, please email us at " +
                WWW.SUPPORT_EMAIL_ADDRESS.get() +
                " immediately and we will take the necessary steps to secure your account.\n" +
                "\n" +
                "You can manage " + manageDeviceStringAndURL + ".";

        email.addSection(subject, HEADER_SIZE.H1, body);
        email.addDefaultSignature();

        try {
            EmailSender.sendPublicEmailFromSupport(SPParam.EMAIL_FROM_NAME, emailAddress, null,
                    subject, email.getTextEmail(), email.getHTMLEmail(),
                    EmailCategory.DEVICE_CERTIFIED);
        } catch (MessagingException e) {
            throw new IOException(e);
        }

        EmailUtil.emailSPNotification(emailAddress + " device certified email.",
                "device id: " + did.toStringFormal());
    }
}
