package com.aerofs.lib.ex;

import com.aerofs.lib.formatted.MessageFormatters;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

import java.io.File;

public class ExNotDir extends AbstractExObfuscatedWirable
{
    private static final long serialVersionUID = 1L;

    public ExNotDir()
    {
        super();
    }

    public ExNotDir(String message, File file)
    {
        super(MessageFormatters.formatFileMessage(message, file));
    }

    public ExNotDir(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NOT_DIR;
    }
}
