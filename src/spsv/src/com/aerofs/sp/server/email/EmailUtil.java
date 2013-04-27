/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.servlets.lib.EmailSender;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.base.Loggers;
import org.slf4j.Logger;

public class EmailUtil
{
    private static final Logger l = Loggers.getLogger(EmailUtil.class);

    public static void emailSPNotification(final String subject, final String body)
    {
        try {
            EmailSender.sendEmail(SPParam.SP_NOTIFICATION_SENDER_EMAIL_ADDRESS.get(),
                    SPParam.SP_NOTIFICATION_SENDER_EMAIL_ADDRESS.get(),
                    SPParam.SP_NOTIFICATION_RECEIVER_EMAIL_ADDRESS.get(), null, subject, body, null,
                    false, null);
        } catch (Exception e) {
            l.error("cannot email notification: ", e);
        }
    }
}
