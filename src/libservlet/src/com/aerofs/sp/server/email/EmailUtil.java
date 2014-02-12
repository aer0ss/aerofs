/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.base.Loggers;
import org.slf4j.Logger;

public class EmailUtil
{
    private static final Logger l = Loggers.getLogger(EmailUtil.class);
    private static final AsyncEmailSender _emailSender = AsyncEmailSender.createDeprecatedForLogEmails();

    /**
     * Send email notification to the AeroFS team.
     */
    public static void emailInternalNotification(final String subject, final String body)
    {
        // Don't send notification in private deployment
        if (PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT) return;

        try {
            String sender = "sp@aerofs.com";
            _emailSender.sendDeprecatedNotificationEmail(sender, sender, "team@aerofs.com", null,
                    subject, body, null);
        } catch (Exception e) {
            l.error("cannot email notification: ", e);
        }
    }
}
