/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.aerofs.base.Loggers;
import com.aerofs.ids.UserID;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.sp.server.email.InvitationReminderEmailer;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.SPParam;
import org.slf4j.Logger;

import javax.inject.Inject;

public class InvitationReminder
{
    private static final Logger l = Loggers.getLogger(InvitationReminder.class);
    private final ScheduledExecutorService _executor = Executors.newScheduledThreadPool(1);
    private final InvitationReminderEmailer _emailer;

    private final SQLThreadLocalTransaction _trans;
    private final EmailSubscriptionDatabase _db;
    /**
     * Remind at intervals of 2 days, 10 days, and 30 days
     */
    private static final int[] REMINDER_INTERVALS = { 2, 10, 30 };

    private static final int TWO_DAYS = 48;

    /**
     * how many users to request at a time from the database
     *
     * The number is set to 100 to be at a reasonable size to prevent holding the database
     * lock for too long, but still useful enough to batch the getUsersNotSignedUpAfterXDays
     * operation. It is important to batch the getUsersNotSignedUpAfterXDays operation because
     * it performs a join on two tables, so we want to minimize how often the function is called
     *
     * This number can be adjusted later to be larger or smaller depending on performance
     * requirements.
     */
    private static final int MAX_USERS = 100;

    @Inject
    public InvitationReminder(EmailSubscriptionDatabase db, SQLThreadLocalTransaction trans,
            InvitationReminderEmailer emailer)
    {
        _db = db;
        _trans = trans;
        _emailer = emailer;
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

    public void remind(int[] intervals)
    {
        try {
            for (int interval : intervals) {
                l.info("Checking for users not signed up after " + interval + " days");

                Set<UserID> users;

                /**
                 * The offset is used for splitting up the getUsersNotSignedUp call into
                 * multiple calls for better performance and memory utilizaiton across
                 * larger user bases.
                 *
                 * NOTE: It's possible that between calls new users will become eligible
                 * for an email reminder, and we may miss these users until the next day if they
                 * are ordered earlier than the offset we're currently at. This is ok,
                 * since we'll just get them the next time the remind code runs.
                 */
                int offset = 0;

                /*
                 * In the interest of holding the lock for a short period of time,
                 * we want to separate getting the list of users, and sending the emails.
                 *
                 * We do this because email sending is slow, as we have to wait for a succesful
                 * return from the Email Sender (SV in this case, but it could be any other
                 * service).
                 *
                 * In this code we're balancing two operations that access three tables:
                 *  - getUsersNotSignedUpAfterXDays involves looking up two SP Tables
                 *   (sp_user and sp_signup_codes). This operation can be batched to improve
                 *   overall performance.
                 *
                 *  - The second operation is sending the emails themselves, which touches
                 *    the sp_email_subscriptions table, and can be split up into separate
                 *    transactions to reduce the time we hold the lock on this table.
                 *    We want to minimize the time the sp_email_subscriptions table is locked
                 *    because it is also used during the invitation and sign up flows
                 *    (during invitation we add a subscription, during sign up we remove a
                 *    subscription from the email reminders).
                 */

                do {
                    _trans.begin();
                    users = _db.getUsersNotSignedUpAfterXDays(interval, MAX_USERS, offset);
                    _trans.commit();

                    sendEmails(users);

                    offset += users.size();

                } while (!users.isEmpty());
            }
        } catch (Exception e) {
            l.warn("ignored: ", e);
            _trans.handleException();
        }
    }

    protected void sendEmails(Set<UserID> users)
            throws Exception
    {
        for (UserID user : users) {
            _trans.begin();

            // make sure we don't send an emailreminder twice in 48 hours
            if (_db.getHoursSinceLastEmail(user,
                    SubscriptionCategory.AEROFS_INVITATION_REMINDER) >= TWO_DAYS) {

                l.info("notifying " + user);
                String signupCode = _db.getOneSignUpCode(user);

                _db.setLastEmailTime(user, SubscriptionCategory.AEROFS_INVITATION_REMINDER,
                        System.currentTimeMillis());

                String unsubscribeTokenId =
                        _db.getTokenId(user, SubscriptionCategory.AEROFS_INVITATION_REMINDER);
                _emailer.send(SPParam.EMAIL_FROM_NAME, user.getString(), signupCode,
                        unsubscribeTokenId);
            }

            _trans.commit();
        }
    }
}
