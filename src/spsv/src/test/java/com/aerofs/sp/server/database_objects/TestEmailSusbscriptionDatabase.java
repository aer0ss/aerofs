/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.database_objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.UserID;
import com.aerofs.sp.server.AbstractAutoTransactionedTestWithSPDatabase;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;

import java.util.EnumSet;

import java.util.Set;

import java.sql.SQLException;

import org.junit.Test;

import com.aerofs.sp.common.SubscriptionCategory;

public class TestEmailSusbscriptionDatabase extends AbstractAutoTransactionedTestWithSPDatabase
{
    private static final UserID TEST_USER1 = UserID.fromInternal("test@test.com");
    private static final UserID TEST_USER2 = UserID.fromInternal("test2@test.com");

    protected EmailSubscriptionDatabase esdb = new EmailSubscriptionDatabase(sqlTrans);

    @Test
    public void shouldSubscribeUserToOneCategory()
            throws SQLException
    {
        SubscriptionCategory sc = SubscriptionCategory.AEROFS_INVITATION_REMINDER;

        esdb.insertEmailSubscription(TEST_USER1, sc);

        assertTrue(esdb.isSubscribed(TEST_USER1, sc));
    }

    @Test
    public void shouldUnsubscribeUserFromOneCategory()
            throws SQLException, ExNotFound
    {
        SubscriptionCategory sc1 = SubscriptionCategory.AEROFS_INVITATION_REMINDER;
        SubscriptionCategory sc2 = SubscriptionCategory.NEWSLETTER;

        esdb.insertEmailSubscription(TEST_USER2, sc1);
        esdb.insertEmailSubscription(TEST_USER2, sc2);

        Set<SubscriptionCategory> subscriptions = esdb.getEmailSubscriptions(TEST_USER2.getString());

        //sanity check token management
        String subscriptionToken = esdb.getTokenId(TEST_USER2, sc1);
        String emailFromToken = esdb.getEmail(subscriptionToken);
        assertEquals(emailFromToken, TEST_USER2.getString());

        assertEquals(EnumSet.of(sc1,sc2), subscriptions);

        esdb.removeEmailSubscription(TEST_USER2, sc2);

        assertFalse(esdb.isSubscribed(TEST_USER2, sc2));
        assertTrue(esdb.isSubscribed(TEST_USER2, sc1));
    }
}
