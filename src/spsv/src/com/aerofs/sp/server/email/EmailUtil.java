/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.base.Loggers;
import org.slf4j.Logger;

public class EmailUtil
{
    private static final Logger l = Loggers.getLogger(EmailUtil.class);
    private static final AsyncEmailSender _emailSender = new AsyncEmailSender();

    public static void emailSPNotification(final String subject, final String body)
    {
        try {
            _emailSender.sendNotificationEmail(SPParam.Notifications.SENDER_EMAIL_ADDRESS,
                    SPParam.Notifications.SENDER_EMAIL_ADDRESS,
                    SPParam.Notifications.RECEIVER_EMAIL_ADDRESS, null, subject, body, null);
        } catch (Exception e) {
            l.error("cannot email notification: ", e);
        }
    }
}