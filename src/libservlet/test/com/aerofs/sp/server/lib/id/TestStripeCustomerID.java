/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.id;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class TestStripeCustomerID
{
    private static final String ACTUAL_STRIPE_CUSTOMER_ID = "cus_17GQKmjD4CqCcM";

    @Test(expected = NullPointerException.class)
    public void shouldThrowOnNullIDs()
    {
        StripeCustomerID.create(null);
    }

    @Test
    public void shouldHoldActualStripeCustomerID()
    {
        StripeCustomerID result = StripeCustomerID.create(ACTUAL_STRIPE_CUSTOMER_ID);
        assertEquals(ACTUAL_STRIPE_CUSTOMER_ID, result.getID());
    }
}
