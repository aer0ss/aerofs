/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

import com.aerofs.lib.Path;

import java.io.File;

/**
 * Analogous to FileNotFound but part of the ExFileIO exception hierarchy
 * which allows for obfuscation of file paths.
 */
public class ExFileNotFound extends ExFileIO
{
    private static final long serialVersionUID = 1L;

    public ExFileNotFound(File file)
    {
        super("file {} does not exist", file);
    }

    public ExFileNotFound(Path path)
    {
        super("path {} does not exist", path);
    }
}
