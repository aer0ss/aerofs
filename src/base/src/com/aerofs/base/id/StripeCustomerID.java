/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.id;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Strings;

// TODO (eric) refactor StringID, it should be a contract with an abstract implementation... and it
// should be generic instead of type specific
public class StripeCustomerID extends StringID {
    // TODO (eric) tests in muliple jar's depend on this value, we do not have test-jars setup properly
    // so I can not move this to the test package at this time.
    // http://maven.apache.org/guides/mini/guide-attached-tests.html
    @VisibleForTesting
    public static final StripeCustomerID TEST = newInstance("cus_17GQKmjD4CqCcM");

    private StripeCustomerID(final String stripeCustomerId) {
        super(stripeCustomerId);
    }

    // TODO (eric) move equals, hashCode, and toString to StringID
    @Override
    public boolean equals(final Object other) {
        if (other == null) {
            return false;
        }

        if (!(other instanceof StripeCustomerID)) {
            return false;
        }

        final StripeCustomerID otherStripeCustomerID = (StripeCustomerID) other;

        return Objects.equal(getID(), otherStripeCustomerID.getID());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getID());
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .omitNullValues()
                .add("id", getID())
                .toString();
    }

    /**
     * Create a new StripeCustomerID
     * @param stripeCustomerId
     * @return
     */
    public static StripeCustomerID newInstance(final String stripeCustomerId) {
        final String notNullStripeCustomerId = Strings.nullToEmpty(stripeCustomerId).trim();

        if (notNullStripeCustomerId.isEmpty()) {
            return null;
        }

        return new StripeCustomerID(notNullStripeCustomerId);
    }
}
