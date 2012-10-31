/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.lib.Util;
import com.aerofs.lib.spsv.SVClient;
import org.apache.log4j.Logger;

import static com.aerofs.sp.server.SPParam.*;

public class EmailUtil
{
    private static final Logger l = Util.l(EmailUtil.class);

    public static void emailSPNotification(final String subject, final String body)
    {
        try {
            SVClient.sendEmail(SP_NOTIFICATION_SENDER, SP_NOTIFICATION_SENDER,
                    SP_NOTIFICATION_RECEIVER, null, subject, body, null, false, null);
        } catch (Exception e) {
            l.error("cannot email notification: ", e);
        }
    }
}
