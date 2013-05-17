/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNoComponentWithSpecifiedVersion extends AbstractExWirable implements IExPermanentError
{
    private static final long serialVersionUID = 1L;

    public ExNoComponentWithSpecifiedVersion()
    {
        // Avoid using the type name as the error message since it would be too long and verbose.
        // See AbstractExWirable.getMessage()
        super("ncwsv");
    }

    public ExNoComponentWithSpecifiedVersion(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NO_COMPONENT_WITH_SPECIFIED_VERSION;
    }
}
