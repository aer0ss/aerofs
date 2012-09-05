/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.aerofs.lib.Param.SV;
import com.aerofs.lib.spsv.sendgrid.SubscriptionCategory;
import com.aerofs.servlets.lib.db.SQLThreadLocalTransaction;
import com.aerofs.sp.server.SPParam;
import com.aerofs.sp.server.lib.SPDatabase;
import org.apache.log4j.Logger;

import com.aerofs.lib.Util;
import com.aerofs.sp.server.email.InvitationReminderEmailer;

public class EmailReminder
{
    private static final Logger l = Util.l(EmailReminder.class);
    private final ScheduledExecutorService _executor = Executors.newScheduledThreadPool(1);
    private final InvitationReminderEmailer.Factory _emailFactory;

    private final SQLThreadLocalTransaction _trans;
    private final SPDatabase _db;
    /**
     * Remind at intervals of 2 days, 10 days, and 30 days
     */
    private static final int[] REMINDER_INTERVALS = { 2, 10, 30 };

    public EmailReminder(final SPDatabase db, final SQLThreadLocalTransaction trans,
            final InvitationReminderEmailer.Factory emailFactory)
    {
        _db = db;
        _trans = trans;
        _emailFactory = emailFactory;
        l.info("Initialized Email Reminder");
    }

    public void start()
    {
        _executor.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                l.info("running email reminder");
                remind(REMINDER_INTERVALS);
            }
        },0,1, TimeUnit.DAYS);
    }

    protected void remind(int[] reminders) {
        try {
            _trans.begin();
            for (int interval : reminders) {
                l.info("Checking for users not signed up after " + interval + " days");
                Set<String> users = _db.getUsersNotSignedUpAfterXDays(interval);
                sendEmails(users);
            }
            _trans.commit();
        } catch (Exception e) {
            l.warn("EmailReminder: ", e);
            _trans.handleException();
        }
    }
    protected void sendEmails(Set<String> users)
            throws Exception
    {
        for (String user : users) {
            l.info("notifying " + user);
            if (_db.getDaysFromLastEmail(user,
                    SubscriptionCategory.AEROFS_INVITATION_REMINDER) > 0) {

                String signupCode = _db.getOnePendingFolderInvitationCode(user);

                _db.setLastEmailTime(user, SubscriptionCategory.AEROFS_INVITATION_REMINDER,
                        System.currentTimeMillis());

                String blobId =
                        _db.getTokenId(user, SubscriptionCategory.AEROFS_INVITATION_REMINDER);
                _emailFactory.createReminderEmail(SV.SUPPORT_EMAIL_ADDRESS,
                        SPParam.SP_EMAIL_NAME, user, signupCode, blobId).send();
            }
        }
    }
}
