/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sv;

import com.aerofs.lib.spsv.sendgrid.Sendgrid.Category;
import org.junit.Test;

import java.sql.SQLException;
import java.util.EnumSet;

import static org.junit.Assert.assertTrue;


public class TestSVEmailSubscriptionManagement extends AbstractSVReactorTest
{
    private static final String TEST_USER1 = "test@test.com";
    private static final String TEST_USER2 = "test2@test.com";

    @Test
    public void shouldSubscribeUserToAllCategories()
            throws SQLException {
        db.subscribeAllEmails(TEST_USER1);

        assertTrue(db.getEmailSubscriptions(TEST_USER1).equals(EnumSet.allOf(Category.class)));
    }

    @Test
    public void shouldUnsubscribeUserFromOneCategory()
        throws SQLException {

        db.subscribeAllEmails(TEST_USER2);

        db.modifyEmailSubscription(TEST_USER2,Category.SUPPORT, false);

        EnumSet<Category> expected = EnumSet.allOf(Category.class);
        expected.remove(Category.SUPPORT);

        assertTrue(db.getEmailSubscriptions(TEST_USER2).
                        equals(expected));
    }
}
