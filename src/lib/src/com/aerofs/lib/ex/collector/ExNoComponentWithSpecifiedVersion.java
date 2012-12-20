package com.aerofs.lib.ex.collector;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNoComponentWithSpecifiedVersion extends AbstractExPermanentError
{
    private static final long serialVersionUID = 1L;

    public ExNoComponentWithSpecifiedVersion()
    {
        super();
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
