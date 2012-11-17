/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.obfuscate;

import java.io.File;

/**
 * Obfuscates file paths taken from a File object.
 */
class FileObjectObfuscator implements IObfuscator<File>
{
    @Override
    public String obfuscate(File f)
    {
        return ObfuscatingFormatters.obfuscatePath(f.getAbsolutePath());
    }

    @Override
    public String plainText(File f)
    {
        return f.getAbsolutePath();
    }
}
