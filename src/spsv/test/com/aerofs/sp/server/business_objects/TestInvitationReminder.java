/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.C;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.common.Base62CodeGenerator;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.sp.server.InvitationReminder;
import com.aerofs.sp.server.email.InvitationReminderEmailer;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.base.id.OrganizationID;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;

import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// TODO (WW) As a test for business objects, it should not operate on database objects directly
// other than setting up the test.
public class TestInvitationReminder extends AbstractBusinessObjectTest
{
    @Mock private InvitationReminderEmailer _emailer;

    @InjectMocks private InvitationReminder er;

    private static final OrganizationID ORG_ID = new OrganizationID(543);

    private static final int TWO_DAYS_INT = 2;
    private static final long TWO_DAYS_IN_MILLISEC = TWO_DAYS_INT * C.DAY;
    private Set<UserID> _twoDayUsers;
    private static final int NUM_TWO_DAY_USERS = 150;
    private static final String TWO_DAY_USERS_PREFIX = "two";

    private static final int THREE_DAYS_INT = 3;
    private static final long THREE_DAYS_IN_MILLISEC = THREE_DAYS_INT * C.DAY;
    private Set<UserID> _threeDayUsers;
    private static final int NUM_THREE_DAY_USERS = 10;
    private static final String THREE_DAY_USERS_PREFIX = "three";

    private static final String USERS_SUFFIX = "@aerofs.com";

    private static final int NUM_USERS_TO_RETURN_IN_SET = 100;

    @Before
    public void setupTestSpEmailReminder()
            throws Exception
    {
        odb.insert(ORG_ID);

        // an extra hour was added to the age because of a bug in how we computer the hour
        // difference. DST was not taken into account and a hard 2 days cut will cause
        // this unit test to break if run within 48 hours after DST ends.
        // see EmailSubscriptionDatabase.getHoursSinceLastEmail() for details.
        _twoDayUsers = setupUsers(NUM_TWO_DAY_USERS, TWO_DAYS_IN_MILLISEC + C.HOUR,
                                    TWO_DAY_USERS_PREFIX);
        _threeDayUsers = setupUsers(NUM_THREE_DAY_USERS, THREE_DAYS_IN_MILLISEC,
                                    THREE_DAY_USERS_PREFIX);
    }

    private Set<UserID> setupUsers(final int count, final long age,
                            final String prefix)
            throws Exception
    {
        Set<UserID> users = Sets.newHashSetWithExpectedSize(count);

        //setup user email addresses
        for (int i = 0; i < count; i ++) {
            users.add(UserID.fromInternal(prefix + i + USERS_SUFFIX));
        }

        for (UserID user: users) {
            l.info("adding signup code for: " + user);
            String signupCode = Base62CodeGenerator.generate();
            udb.insertSignupCode(signupCode, user, System.currentTimeMillis() - age);

            esdb.insertEmailSubscription(user, SubscriptionCategory.AEROFS_INVITATION_REMINDER,
                    System.currentTimeMillis() - age);

            assertNotNull(db.getSignUpCode(signupCode));
        }

        return users;
    }

    @Test
    public void shouldReturnCorrectUserSetWhenCheckingNonSignedUpUsers()
        throws Exception
    {
        int offset = 0;

        Set<UserID> users;

        do {
            users = esdb.getUsersNotSignedUpAfterXDays(TWO_DAYS_INT, NUM_USERS_TO_RETURN_IN_SET,
                      offset);

            // assert that the returned set of users is a subset of the full set of two-day users
            assertTrue(Sets.difference(users, _twoDayUsers).isEmpty());

            offset += users.size();

        } while (!users.isEmpty());

        assertEquals(offset, _twoDayUsers.size());
    }

    @Test
    public void shouldEmailRemindersOnlyOnceInAFourtyEightHourPeriod()
            throws Exception
    {

        final int[] interval = { 2 };
        // try and send two emails to the same set of user.
        // remind should send emails the first time, and should simply
        // return the second time.

        // commit the current transaction as we are in auto-transaction mode. See
        // (AbstractAutoTransactionedTestWithSPDatabase)
        sqlTrans.commit();

        er.remind(interval);
        er.remind(interval);

        // see the above comment for trans.commit().
        sqlTrans.begin();

        //verify that a user only would have one email sent to them
        verifyEmailRemindersForUsers(_twoDayUsers, times(1));

        //verify that we don't email other users (e.g. users we emailed three days ago)
        verifyEmailRemindersForUsers(_threeDayUsers, never());
    }

    private void verifyEmailRemindersForUsers(Set<UserID> users, VerificationMode mode)
            throws Exception
    {
        for (UserID user: users) {
            String tokenId = esdb.getTokenId(user, SubscriptionCategory.AEROFS_INVITATION_REMINDER);

            verify(_emailer, mode).send(eq(SPParam.EMAIL_FROM_NAME), eq(user.getString()),
                    anyString(), eq(tokenId));
        }
    }
}
