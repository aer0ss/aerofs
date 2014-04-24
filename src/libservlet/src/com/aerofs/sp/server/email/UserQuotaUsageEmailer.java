/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.C;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sv.common.EmailCategory;

import javax.mail.MessagingException;
import java.io.IOException;

public class UserQuotaUsageEmailer
{
    private final AsyncEmailSender _emailSender;

    public UserQuotaUsageEmailer(AsyncEmailSender emailSender)
    {
        _emailSender = emailSender;
    }

    public void sendEmail(String toEmail, long bytesUsed, long bytesAllowed)
            throws IOException, MessagingException
    {
        int percentUsed = (int)((double)bytesUsed * 100 / (double)bytesAllowed);
        String subject = getEmailSubject(percentUsed);
        String body = getEmailBody((int)(bytesUsed / C.GB), (int)(bytesAllowed / C.GB), percentUsed);

        Email email = new Email();
        email.addSection(subject, body);
        email.addDefaultSignature();

        _emailSender.sendPublicEmail(WWW.SUPPORT_EMAIL_ADDRESS, SPParam.EMAIL_FROM_NAME, toEmail,
                null, subject, email.getTextEmail(), email.getHTMLEmail(),
                EmailCategory.QUOTA_WARNING);
    }

    private static String getEmailSubject(int percentQuotaUsed)
    {
        return "You have used " + Integer.toString(percentQuotaUsed) + "% of your" +
                " space on the Team Server";
    }

    private static String getEmailBody(int usedGB, int quotaGB, int percentQuotaUsed)
    {
        return "\nYour AeroFS folder is automatically backed up to a Team Server. You are "
                + "currently using " + Integer.toString(usedGB) + "GB ("
                + Integer.toString(percentQuotaUsed) + "%) of the allowed "
                + Integer.toString(quotaGB) + "GB.\n"
                + "\n"
                + "If your AeroFS folder is larger than " + Integer.toString(quotaGB) + "GB, new "
                + "files will not be backed up to the Team Server and may not be visible on the "
                + "AeroFS iOS and Android apps or the My Files web interface. All of your files "
                + "will still be synced across your devices and to other people with whom you have "
                + "shared folders.\n"
                + "\n"
                + "You can free up space at any time by moving files out of the AeroFS folder.";
    }
}
