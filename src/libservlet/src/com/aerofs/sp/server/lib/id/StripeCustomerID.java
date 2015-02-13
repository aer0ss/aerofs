/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.id;

import com.aerofs.ids.StringID;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO (eric) refactor StringID, it should be a contract with an abstract implementation... and it
// should be generic instead of type specific
public class StripeCustomerID extends StringID
{
    private StripeCustomerID(final String stripeCustomerId)
    {
        super(stripeCustomerId);
    }

    @Override
    public boolean equals(final Object other)
    {
        return other == this || (other != null && ((StripeCustomerID) other).getString().equals(
                getString()));
    }

    @Override
    public int hashCode()
    {
        return getString().hashCode();
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
