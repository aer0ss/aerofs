/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.base.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExSecondFactorSetupRequired extends AbstractExWirable
{
    private static final long serialVersionUID = 0;

    public ExSecondFactorSetupRequired()
    {
        super();
    }

    public ExSecondFactorSetupRequired(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.SECOND_FACTOR_SETUP_REQUIRED;
    }
}
