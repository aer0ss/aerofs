package com.aerofs.daemon.core.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExUpdateInProgress extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExUpdateInProgress()
    {
        super();
    }

    public ExUpdateInProgress(String string)
    {
        super(string);
    }

    /**
     * Do not remove. It's used by Exceptions.fromPB()
     */
    public ExUpdateInProgress(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.UPDATE_IN_PROGRESS;
    }

}
