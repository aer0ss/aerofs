/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

/**
 * An organization is on a paid plan but doesn't have a Stripe customer ID (which is required for
 * charging).
 */
public class ExNoStripeCustomerID extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExNoStripeCustomerID()
    {
        super();
    }

    public ExNoStripeCustomerID(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NO_STRIPE_CUSTOMER_ID;
    }
}
