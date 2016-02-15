package com.aerofs.lib.ex;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

/**
 * See the comment for PBException.Type.UI_MESSAGE for usage.
 */
public class ExUIMessage extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public ExUIMessage(String msg)
    {
        super(msg);
    }

    public ExUIMessage(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.UI_MESSAGE;
    }
}
