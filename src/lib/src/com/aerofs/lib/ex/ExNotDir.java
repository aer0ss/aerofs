package com.aerofs.lib.ex;

import com.aerofs.lib.obfuscate.ObfuscatingFormatters;
import com.aerofs.lib.obfuscate.ObfuscatingFormatter.FormattedMessage;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;

import java.io.File;

public class ExNotDir extends AbstractExWirable implements IExObfuscated
{
    private static final long serialVersionUID = 1L;

    private final String _obfuscatedMessage;
    private final String _plainTextMessage;

    public ExNotDir()
    {
        super();
        _obfuscatedMessage = null;
        _plainTextMessage = "";
    }

    public ExNotDir(String message, File... files)
    {
        // Unfortunately we can't use the base class' constructor for initializing the
        // obfuscated message because we must first format the message and extract the
        // obfuscated message from the returned data structure. Java does not let you call
        // the super constructor if it's not the first thing called.
        super();

        FormattedMessage formattedMessage = ObfuscatingFormatters.formatFileMessage(message, files);
        _obfuscatedMessage = formattedMessage._obfuscated;
        _plainTextMessage = formattedMessage._plainText;
    }

    public ExNotDir(PBException pb)
    {
        super(pb);
        assert(pb.hasPlainTextMessage());
        _obfuscatedMessage = null;
        _plainTextMessage = pb.getPlainTextMessage();
    }

    @Override
    public String getPlainTextMessage()
    {
        return _plainTextMessage;
    }

    /*
     * We have to override this due to the issue that we can't set the obfuscated message
     * in the super class' constructor.
     */
    @Override
    public String getMessage()
    {
        if (_obfuscatedMessage == null) {
            return super.getMessage();
        }
        return _obfuscatedMessage;
    }

    @Override
    public Type getWireType()
    {
        return Type.NOT_DIR;
    }
}
