/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

import com.aerofs.base.ex.IExObfuscated;
import com.aerofs.lib.Path;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.formatted.FormattedMessage;
import com.aerofs.lib.formatted.MessageFormatters;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Analogous to FileNotFound but part of the ExFileIO exception hierarchy
 * which allows for obfuscation of file paths.
 */
public class ExFileNotFound extends FileNotFoundException implements IExObfuscated
{
    private static final long serialVersionUID = 1L;

    private final String _plainTextMessage;

    public ExFileNotFound(InjectableFile file)
    {
        this(file.getImplementation());
    }

    public ExFileNotFound(File file)
    {
        this(MessageFormatters.formatFileMessage("file {} not found", file));
    }

    public ExFileNotFound(Path path)
    {
        this(MessageFormatters.formatPathMessage("path {} not found", path));
    }

    private ExFileNotFound(FormattedMessage formattedMessage)
    {
        super(formattedMessage._internal);
        _plainTextMessage = formattedMessage._plainText;
    }

    @Override
    public String getPlainTextMessage()
    {
        return _plainTextMessage;
    }
}
