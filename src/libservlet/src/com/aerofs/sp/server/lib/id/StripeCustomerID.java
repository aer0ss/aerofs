/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.id;

import com.aerofs.base.id.StringID;
import com.google.common.annotations.VisibleForTesting;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO (eric) refactor StringID, it should be a contract with an abstract implementation... and it
// should be generic instead of type specific
public class StripeCustomerID extends StringID
{
    // TODO (eric) tests in muliple jar's depend on this value, we do not have test-jars setup properly
    // so I can not move this to the test package at this time.
    // http://maven.apache.org/guides/mini/guide-attached-tests.html
    @VisibleForTesting
    public static final StripeCustomerID TEST = create("cus_17GQKmjD4CqCcM");

    private StripeCustomerID(final String stripeCustomerId)
    {
        super(stripeCustomerId);
    }

    @Override
    public boolean equals(final Object other)
    {
        return other == this || (other != null && ((StripeCustomerID) other).getID().equals(getID()));
    }

    @Override
    public int hashCode()
    {
        return getID().hashCode();
    }

    /**
     * Create a new StripeCustomerID
     */
    public static StripeCustomerID create(final String id)
    {
        checkNotNull(id);
        return new StripeCustomerID(id);
    }
}
