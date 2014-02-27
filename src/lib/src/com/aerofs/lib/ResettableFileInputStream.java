/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * A simple wrapper around FileInputStream that supports reset()
 */
public class ResettableFileInputStream extends InputStream
{
    private final File _f;
    private FileInputStream _i;

    public ResettableFileInputStream(File f) throws FileNotFoundException
    {
        _f = f;
        _i = new FileInputStream(_f);
    }

    @Override
    public synchronized void reset() throws IOException
    {
        _i.close();
        _i = new FileInputStream(_f);
    }

    @Override
    public int read() throws IOException
    {
        return _i.read();
    }

    @Override
    public int read(byte[] bytes) throws IOException
    {
        return _i.read(bytes);
    }

    @Override
    public int read(byte[] bytes, int i, int i1) throws IOException
    {
        return _i.read(bytes, i, i1);
    }

    @Override
    public long skip(long l) throws IOException
    {
        return _i.skip(l);
    }

    @Override
    public int available() throws IOException
    {
        return _i.available();
    }

    @Override
    public void close() throws IOException
    {
        _i.close();
    }
}
