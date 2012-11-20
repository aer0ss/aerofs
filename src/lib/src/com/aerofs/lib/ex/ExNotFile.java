package com.aerofs.lib.ex;

import com.aerofs.lib.Path;
import com.aerofs.lib.obfuscate.ObfuscatingFormatters;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

public class ExNotFile extends AbstractExWirable implements IExObfuscated
{
    private static final long serialVersionUID = 1L;

    private final String _plainTextMessage;

    public ExNotFile()
    {
        super();
        _plainTextMessage = "";
    }

    public ExNotFile(Path path)
    {
        super(ObfuscatingFormatters.obfuscatePath(path));
        _plainTextMessage = path.toString();
    }

    public ExNotFile(PBException pb)
    {
        super(pb);
        assert(pb.hasPlainTextMessage());
        _plainTextMessage = pb.getPlainTextMessage();
    }

    @Override
    public String getPlainTextMessage()
    {
        return _plainTextMessage;
    }

    @Override
    public Type getWireType()
    {
        return Type.NOT_FILE;
    }
}
