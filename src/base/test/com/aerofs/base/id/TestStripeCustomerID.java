/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.id;

import com.aerofs.base.id.StripeCustomerID;
import org.junit.Test;

import static org.junit.Assert.*;

public class TestStripeCustomerID
{
    private static final String ACTUAL_STRIPE_CUSTOMER_ID = "cus_17GQKmjD4CqCcM";
    public static final StripeCustomerID TEST = StripeCustomerID.newInstance(ACTUAL_STRIPE_CUSTOMER_ID);

    @Test
    public void testNullStripeCustomerIDIsNull() {
        final StripeCustomerID result = StripeCustomerID.newInstance(null);
        assertNull(result);
    }

    @Test
    public void testEmptyStripeCustomerIDIsNull() {
        final StripeCustomerID result = StripeCustomerID.newInstance("");
        assertNull(result);
    }

    @Test
    public void testBlankStripeCustomerIDIsNull() {
        final StripeCustomerID result = StripeCustomerID.newInstance("     ");
        assertNull(result);
    }

    @Test
    public void testActualStripeCustomerID() {
        final StripeCustomerID result = StripeCustomerID.newInstance(ACTUAL_STRIPE_CUSTOMER_ID);
        assertNotNull(result);
        assertEquals(ACTUAL_STRIPE_CUSTOMER_ID, result.getID());
    }
}
