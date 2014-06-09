/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.base.ex;

import com.aerofs.base.BaseUtil;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExSecondFactorRequired extends AbstractExWirable
{
    private static final long serialVersionUID = 0;

    public ExSecondFactorRequired()
    {
        super();
    }

    public ExSecondFactorRequired(int failures)
    {
        super(BaseUtil.string2utf(String.valueOf(failures)));
    }

    public ExSecondFactorRequired(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.SECOND_FACTOR_REQUIRED;
    }

    public int getFailureCount()
    {
        byte[] data = getDataNullable();
        if (data == null) return 0;
        return Integer.valueOf(BaseUtil.utf2string(data), 10);
    }
}
