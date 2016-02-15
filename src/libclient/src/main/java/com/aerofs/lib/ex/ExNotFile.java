package com.aerofs.lib.ex;

import com.aerofs.lib.Path;
import com.aerofs.lib.formatted.MessageFormatters;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNotFile extends AbstractExObfuscatedWirable
{
    private static final long serialVersionUID = 1L;

    public ExNotFile()
    {
        super();
    }

    public ExNotFile(Path path)
    {
        super(MessageFormatters.formatPathMessage("path {} is not a file", path));
    }

    public ExNotFile(PBException pb)
    {
        super(pb);
    }

    @Override
    public Type getWireType()
    {
        return Type.NOT_FILE;
    }
}
