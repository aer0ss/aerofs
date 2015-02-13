/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.UserID;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.sp.server.email.UserQuotaUsageEmailer;
import com.aerofs.sp.server.lib.user.User;

import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;

/**
 * This class is in charge of emailing users who are near to going over quota, and logging the event
 * to the audit stream.
 */
public class UserQuotaUsageNotifier
{
    private static float WARNING_THRESHOLD = 0.8f;

    private UserQuotaUsageEmailer _emailer;
    private AuditClient _auditClient;

    public UserQuotaUsageNotifier(AsyncEmailSender emailSender, AuditClient auditClient)
    {
        _emailer = new UserQuotaUsageEmailer(emailSender);
        _auditClient = auditClient;
    }

    /**
     * Sets the bytesUsed value for the user, and notifies via email/audit if the value
     * is above the threshold.
     */
    public void updateUserBytesUsed(User user, long bytesUsed, long quotaPerUser)
            throws SQLException, IOException, MessagingException, ExNotFound
    {
        float percentQuotaUsed = (float)bytesUsed / (float)quotaPerUser;
        boolean isOver = percentQuotaUsed >= WARNING_THRESHOLD;
        boolean wasOver = user.getUsageWarningSent();

        if (isOver && !wasOver) {
            notifyOverQuotaWarningLimit(user.id(), bytesUsed, quotaPerUser);
        }
        user.setBytesUsed(bytesUsed);
        user.setUsageWarningSent(isOver);
    }

    private void notifyOverQuotaWarningLimit(UserID userID, long bytesUsed, long quotaPerUser)
            throws IOException, MessagingException
    {
        _emailer.sendEmail(userID.getString(), bytesUsed, quotaPerUser);

        _auditClient.event(AuditTopic.USER, "user.quota.warning")
                .add("user", userID.getString())
                .add("bytes_used", bytesUsed)
                .add("bytes_allowed", quotaPerUser)
                .publish();
    }
}
