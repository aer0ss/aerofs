/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

import com.aerofs.lib.obfuscate.ObfuscatingFormatters;
import com.aerofs.lib.obfuscate.ObfuscatingFormatter.FormattedMessage;

import java.io.File;
import java.io.IOException;

/**
 * An Exception containing file paths. Printing the stacktrace or calling getMessage() will
 * return an obfuscated path.
 */
 public class ExFileIO extends IOException implements IExObfuscated
{
    private static final long serialVersionUID = 1L;

    private final String _obfuscatedMessage;
    private final String _plainTextMessage;

    /**
     * Constructs a new ExFileIO instance with the specified files obfuscated. The message can
     * contain the string '{}' which tells this constructor to insert the obfuscated file path
     * in that place. This works exactly like SLF4J's logging string interpolation.
     *
     * @param message The message to display, with '{}' denoting placeholders where the specified
     * file paths will be inserted.
     * @param files The files whose paths will be inserted in the message, or after the message
     * if no '{}' are present.
     */
    public ExFileIO(String message, File... files)
    {
        this(ObfuscatingFormatters.formatFileMessage(message, files));
    }

    public ExFileIO(String message, Iterable<File> files)
    {
        this(ObfuscatingFormatters.formatFileMessage(message, files));
    }

    private ExFileIO(FormattedMessage formattedMessage)
    {
        _obfuscatedMessage = formattedMessage._obfuscated;
        _plainTextMessage = formattedMessage._plainText;
    }

    @Override
    public String getMessage()
    {
        return _obfuscatedMessage;
    }

    @Override
    public String getPlainTextMessage()
    {
        return _plainTextMessage;
    }

}
