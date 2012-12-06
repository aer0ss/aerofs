/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.lib.C;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.sp.common.InvitationCode.CodeType;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.sp.server.AbstractTestWithSPDatabase;
import com.aerofs.sp.server.SPParam;
import com.aerofs.sp.server.email.EmailReminder;
import com.aerofs.sp.server.email.InvitationReminderEmailer;
import com.aerofs.sp.server.email.InvitationReminderEmailer.Factory;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.verification.VerificationMode;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// TODO (WW) As a test for business objects, it should not operate on database objects directly
// other than setting up the test.
public class TestEmailReminder extends AbstractTestWithSPDatabase
{
    @Mock private final Factory _emailFactory = new Factory();

    @Spy protected SPDatabase _db = new SPDatabase(trans);
    @Spy protected UserDatabase _udb = new UserDatabase(trans);
    @Spy protected EmailSubscriptionDatabase _esdb = new EmailSubscriptionDatabase(trans);
    @Spy protected OrganizationDatabase _odb = new OrganizationDatabase(trans);

    @InjectMocks private EmailReminder er;

    private static final OrgID ORG_ID = new OrgID(543);

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
        when(_emailFactory.createReminderEmail(anyString(), anyString(), anyString(),
                anyString(), anyString()))
                .thenReturn(new InvitationReminderEmailer());

        trans.begin();
        l.info("add default organization");
        _odb.add(ORG_ID, "Test Organization");

        _twoDayUsers = setupUsers(NUM_TWO_DAY_USERS, TWO_DAYS_IN_MILLISEC, TWO_DAY_USERS_PREFIX);
        _threeDayUsers = setupUsers(NUM_THREE_DAY_USERS, THREE_DAYS_IN_MILLISEC,
                                    THREE_DAY_USERS_PREFIX);
        trans.commit();

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
            String signupCode = InvitationCode.generate(CodeType.TARGETED_SIGNUP);
            _udb.addSignupCode(signupCode, UserID.fromInternal(SV.SUPPORT_EMAIL_ADDRESS), user,
                    ORG_ID, System.currentTimeMillis() - age);

            _esdb.addEmailSubscription(user,
                    SubscriptionCategory.AEROFS_INVITATION_REMINDER,
                    System.currentTimeMillis() - age);

            assertNotNull(_db.getSignUpInvitation(signupCode));
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
            trans.begin();
            users = _esdb.getUsersNotSignedUpAfterXDays(TWO_DAYS_INT, NUM_USERS_TO_RETURN_IN_SET,
                      offset);
            trans.commit();

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

        er.remind(interval);
        er.remind(interval);

        //verify that a user only would have one email sent to them
        verifyEmailRemindersForUsers(_twoDayUsers, times(1));

        //verify that we don't email other users (e.g. users we emailed three days ago)
        verifyEmailRemindersForUsers(_threeDayUsers, never());
    }

    private void verifyEmailRemindersForUsers(Set<UserID> users, VerificationMode mode)
            throws SQLException, IOException
    {
        for (UserID user: users) {
            trans.begin();
            String tokenId = _esdb.getTokenId(user, SubscriptionCategory.AEROFS_INVITATION_REMINDER);
            trans.commit();

            verify(_emailFactory, mode).createReminderEmail(eq(SV.SUPPORT_EMAIL_ADDRESS),
                    eq(SPParam.SP_EMAIL_NAME), eq(user.toString()), anyString(), eq(tokenId));

        }

    }
}
