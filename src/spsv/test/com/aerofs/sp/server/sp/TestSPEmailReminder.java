/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

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
import org.junit.After;
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

public class TestSPEmailReminder extends AbstractTest {

    @Mock private final InvitationReminderEmailer.Factory _emailFactory = new Factory();

    protected final SPDatabaseParams _dbParams = new SPDatabaseParams();
    @Spy protected final SQLThreadLocalTransaction _transaction =
            new SQLThreadLocalTransaction(_dbParams.getProvider());
    @Spy protected SPDatabase _db = new SPDatabase(_transaction);
    @InjectMocks private EmailReminder er;

    public static final long TWO_DAYS = 2 * C.DAY;
    public static final long THREE_DAYS = 3 * C.DAY;

    public static final String ORG = "default";

    public static final String[] TWO_DAY_USERS = {
        "two_user1@aerofs.com",
        "two_user2@aerofs.com",
        "two_user3@aerofs.com"
        };

    public static final String[] THREE_DAY_USERS = {
        "three_user1@aerofs.com",
        "three_user2@aerofs.com",
        "three_user3@aerofs.com"
    };



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
        setupTargetedSignupCodes(TWO_DAY_USERS, TWO_DAYS);
        setupTargetedSignupCodes(THREE_DAY_USERS, THREE_DAYS);

    }

    @After
    public void tearDown() throws Exception
    {
        _transaction.commit();
    }
    private void setupTargetedSignupCodes(String[] users, long age)
            throws Exception
    {
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


    }
    @Test
    public void shouldEmailRemindersOnlyOnce()
        throws Exception
    {


        Set<String> users = _db.getUsersNotSignedUpAfterXDays(2);
        assert users.size() == TWO_DAY_USERS.length;

        for (String user : TWO_DAY_USERS) {
            assertTrue(users.contains(user));
        }

        //try and send two emails to the same set of users, shouldn't work
        er.sendEmails(users);
        er.sendEmails(users);

        //verify that a user only would have one email sent to them
        for (String user: TWO_DAY_USERS) {
            String blobId = _db.getTokenId(user, SubscriptionCategory.AEROFS_INVITATION_REMINDER);
            verify(_emailFactory).createReminderEmail(eq(SV.SUPPORT_EMAIL_ADDRESS),
                    eq(SPParam.SP_EMAIL_NAME), eq(user), anyString(), eq(blobId));

        }

        //verify that we don't return other users (e.g. users we emailed three days ago)
        for (String user : THREE_DAY_USERS) {
            String blobId = _db.getTokenId(user, SubscriptionCategory.AEROFS_INVITATION_REMINDER);
            verify(_emailFactory, times(0)).createReminderEmail(
                    eq(SV.SUPPORT_EMAIL_ADDRESS),
                    eq(SPParam.SP_EMAIL_NAME),
                    eq(user),
                    anyString(),
                    eq(blobId));
        }

    }
}
