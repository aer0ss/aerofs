/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

import com.aerofs.lib.Param.SV;
import com.aerofs.lib.spsv.sendgrid.SubscriptionCategory;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.SQLThreadLocalTransaction;
import com.aerofs.sp.server.SPParam;
import com.aerofs.sp.server.email.InvitationReminderEmailer.Factory;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.organization.Organization;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import com.aerofs.lib.C;
import com.aerofs.lib.spsv.InvitationCode;
import com.aerofs.lib.spsv.InvitationCode.CodeType;
import com.aerofs.sp.server.email.InvitationReminderEmailer;
import com.aerofs.testlib.AbstractTest;
import org.mockito.verification.VerificationMode;

public class TestSPEmailReminder extends AbstractTest {

    @Mock private final InvitationReminderEmailer.Factory _emailFactory = new Factory();

    protected final SPDatabaseParams _dbParams = new SPDatabaseParams();
    @Spy protected final SQLThreadLocalTransaction _transaction =
            new SQLThreadLocalTransaction(_dbParams.getProvider());
    @Spy protected SPDatabase _db = new SPDatabase(_transaction);
    @InjectMocks private EmailReminder er;

    private static final String ORG = "sperdefault";

    private static final int TWO_DAYS_INT = 2;
    private static final long TWO_DAYS_IN_MILLISEC = TWO_DAYS_INT * C.DAY;
    private Set<String> _twoDayUsers;
    private static final int NUM_TWO_DAY_USERS = 150;
    private static final String TWO_DAY_USERS_PREFIX = "two";

    private static final int THREE_DAYS_INT = 3;
    private static final long THREE_DAYS_IN_MILLISEC = THREE_DAYS_INT * C.DAY;
    private Set<String> _threeDayUsers;
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

        Log.info("initialize database");
        LocalTestDatabaseConfigurator.initializeLocalDatabase(_dbParams);

        _transaction.begin();
        Log.info("add default organization");
        _db.addOrganization(new Organization(ORG, "", "", true));

        _twoDayUsers = setupUsers(NUM_TWO_DAY_USERS, TWO_DAYS_IN_MILLISEC, TWO_DAY_USERS_PREFIX);
        _threeDayUsers = setupUsers(NUM_THREE_DAY_USERS, THREE_DAYS_IN_MILLISEC,
                                    THREE_DAY_USERS_PREFIX);
        _transaction.commit();

    }

    private Set<String> setupUsers(final int count, final long age,
                            final String prefix)
            throws Exception
    {
        Set<String> users = Sets.newHashSetWithExpectedSize(count);

        //setup user email addresses
        for (int i = 0; i < count; i ++) {
            users.add(prefix + i + USERS_SUFFIX);
        }

        for (String user: users) {
            Log.info("adding signup code for: " + user);
            String signupCode = InvitationCode.generate(CodeType.TARGETED_SIGNUP);
            _db.addTargetedSignupCode(signupCode,
                    SV.SUPPORT_EMAIL_ADDRESS,
                    user,
                    ORG,
                    System.currentTimeMillis()-age);

            _db.addEmailSubscription(user,
                    SubscriptionCategory.AEROFS_INVITATION_REMINDER,
                    System.currentTimeMillis()-age);

            assertNotNull(_db.getTargetedSignUp(signupCode));
        }

        return users;
    }

    @Test
    public void shouldReturnCorrectUserSetWhenCheckingNonSignedUpUsers()
        throws Exception
    {

        int offset = 0;

        Set<String> users;

        do {
            _transaction.begin();
            users = _db.getUsersNotSignedUpAfterXDays(TWO_DAYS_INT,
                                                                  NUM_USERS_TO_RETURN_IN_SET,
                                                                  offset);
            _transaction.commit();

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

    private void verifyEmailRemindersForUsers(Set<String> users, VerificationMode mode)
            throws SQLException, IOException
    {
        for (String user: users) {
            _transaction.begin();
            String tokenId = _db.getTokenId(user, SubscriptionCategory.AEROFS_INVITATION_REMINDER);
            _transaction.commit();

            verify(_emailFactory, mode).createReminderEmail(eq(SV.SUPPORT_EMAIL_ADDRESS),
                    eq(SPParam.SP_EMAIL_NAME), eq(user), anyString(), eq(tokenId));

        }

    }
}
