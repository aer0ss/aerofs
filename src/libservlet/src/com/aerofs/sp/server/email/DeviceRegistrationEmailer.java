/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.id.DID;
import com.aerofs.labeling.L;
import com.aerofs.lib.Util;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sp.server.lib.SPParam;

import javax.mail.MessagingException;
import java.io.IOException;

public class DeviceRegistrationEmailer
{
    private static AsyncEmailSender _emailSender = AsyncEmailSender.create();

    public void sendTeamServerDeviceCertifiedEmail(String emailAddress, String firstName,
            String osFamily, String deviceName, DID did)
            throws IOException, MessagingException
    {
        // N.B. the URI string must be identical to that in devices/__init__.py.
        sendDeviceCertifiedEmailImpl(L.brand() + " Team Server", emailAddress, firstName, osFamily,
                deviceName, "Team Servers at " + WWW.TEAM_SERVER_DEVICES_URL, did);
    }

    public void sendDeviceCertifiedEmail(String emailAddress, String firstName,
            String osFamily, String deviceName, DID did)
            throws IOException, MessagingException
    {
        // N.B. the URI string must be identical to that in devices/__init__.py.
        sendDeviceCertifiedEmailImpl(L.brand(), emailAddress, firstName, osFamily, deviceName,
                "your devices at " + WWW.DEVICES_URL, did);
    }

    public void sendDeviceCertifiedEmailImpl(String product, String emailAddress, String firstName,
            String osFamily, String deviceName, String manageDeviceStringAndURL, DID did)
            throws IOException, MessagingException
    {
        String subject = product + " Installed on Your Device " + Util.quote(deviceName);
        String body = "\n" +
                "Hi " + firstName + ",\n" +
                "\n" +
                product + " has recently been installed on a new " + osFamily + " device named " +
                Util.quote(deviceName) + ".\n" +
                "\n" +
                "If this device does not belong to you, please email us at " +
                WWW.SUPPORT_EMAIL_ADDRESS +
                " immediately and we will take the necessary steps to secure your account.\n" +
                "\n" +
                    // Note the space after the URL to enable the autolinker to drop the period.
                    // In the future, real templates could make this more intelligent.
                "You can manage " + manageDeviceStringAndURL + " .";

        Email email = new Email();
        email.addSection(subject, body);
        email.addDefaultSignature();

        _emailSender.sendPublicEmailFromSupport(SPParam.EMAIL_FROM_NAME, emailAddress, null,
                subject, email.getTextEmail(), email.getHTMLEmail());

        EmailUtil.emailInternalNotification(emailAddress + " device certified email.",
                "device id: " + did.toStringFormal());
    }
}
