/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import org.junit.After;

import org.junit.Before;

import java.util.EnumSet;

import com.aerofs.testlib.AbstractTest;

import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.SQLThreadLocalTransaction;

import java.util.Set;

import java.sql.SQLException;

import org.junit.Test;

import com.aerofs.sp.common.SubscriptionCategory;


public class TestEmailSusbscriptionDatabase extends AbstractTest
{
    private static final UserID TEST_USER1 = UserID.fromInternal("test@test.com");
    private static final UserID TEST_USER2 = UserID.fromInternal("test2@test.com");

    protected final SPDatabaseParams _dbParams = new SPDatabaseParams();
    protected final SQLThreadLocalTransaction _transaction =
            new SQLThreadLocalTransaction(_dbParams.getProvider());
    protected EmailSubscriptionDatabase db = new EmailSubscriptionDatabase(_transaction);

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
            throws SQLException, ExNotFound {
        SubscriptionCategory sc1 = SubscriptionCategory.AEROFS_INVITATION_REMINDER;
        SubscriptionCategory sc2 = SubscriptionCategory.NEWSLETTER;

        db.addEmailSubscription(TEST_USER2,sc1);
        db.addEmailSubscription(TEST_USER2,sc2);

        Set<SubscriptionCategory> subscriptions = db.getEmailSubscriptions(TEST_USER2.toString());

        //sanity check token management
        String subscriptionToken = db.getTokenId(TEST_USER2, sc1);
        String emailFromToken = db.getEmail(subscriptionToken);
        assertEquals(emailFromToken, TEST_USER2.toString());

        assertEquals(EnumSet.of(sc1,sc2), subscriptions);

        db.removeEmailSubscription(TEST_USER2, sc2);

        assertFalse(db.isSubscribed(TEST_USER2, sc2));
        assertTrue(db.isSubscribed(TEST_USER2, sc1));
    }
}
