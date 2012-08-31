/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;

import org.junit.Before;

import java.util.EnumSet;

import com.aerofs.testlib.AbstractTest;

import com.aerofs.servletlib.sp.IEmailSubscriptionDatabase;

import com.aerofs.servletlib.db.SPDatabaseParams;
import com.aerofs.servletlib.db.SQLThreadLocalTransaction;
import com.aerofs.servletlib.sp.SPDatabase;

import java.util.Set;

import java.sql.SQLException;

import org.junit.Test;

import com.aerofs.lib.spsv.sendgrid.SubscriptionCategory;


public class TestEmailSusbscriptionDatabase extends AbstractTest
{
    private static final String TEST_USER1 = "test@test.com";
    private static final String TEST_USER2 = "test2@test.com";

    protected final SPDatabaseParams _dbParams = new SPDatabaseParams();
    protected final SQLThreadLocalTransaction _transaction =
            new SQLThreadLocalTransaction(_dbParams.getProvider());
    protected IEmailSubscriptionDatabase db = new SPDatabase(_transaction);

    @Before
    public void beginTx() throws SQLException { _transaction.begin(); }

    @After
    public void commitTx() throws SQLException { _transaction.commit(); }

    @Test
    public void shouldSubscribeUserToOneCategory()
            throws SQLException {
        SubscriptionCategory sc = SubscriptionCategory.AEROFS_INVITATION_REMINDER;

        db.addEmailSubscription(TEST_USER1, sc);

        assertTrue(db.isSubscribed(TEST_USER1, sc));
    }

    @Test
    public void shouldUnsubscribeUserFromOneCategory()
        throws SQLException
   {
        SubscriptionCategory sc1 = SubscriptionCategory.AEROFS_INVITATION_REMINDER;
        SubscriptionCategory sc2 = SubscriptionCategory.NEWSLETTER;

        db.addEmailSubscription(TEST_USER2,sc1);
        db.addEmailSubscription(TEST_USER2,sc2);

        Set<SubscriptionCategory> subscriptions = db.getEmailSubscriptions(TEST_USER2);
        assertEquals(EnumSet.of(sc1,sc2), subscriptions);

        db.removeEmailSubscription(TEST_USER2, sc2);

        assertFalse(db.isSubscribed(TEST_USER2, sc2));
        assertTrue(db.isSubscribed(TEST_USER2, sc1));
    }
}
