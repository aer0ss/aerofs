/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.obfuscate;

import com.aerofs.lib.Path;

import java.io.File;

class PathObfuscater implements IObfuscator<Path>
{
    private IObfuscator<File> _obfuscatorImpl;

    public PathObfuscater(IObfuscator<File> obfuscatorImpl)
    {
        _obfuscatorImpl = obfuscatorImpl;
    }

    @Override
    public String obfuscate(Path object)
    {
        return _obfuscatorImpl.obfuscate(new File(object.toStringFormal()));
    }

    @Override
    public String plainText(Path object)
    {
        return object.toString();
    }
}
