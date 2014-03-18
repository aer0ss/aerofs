/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.gzip;

import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.lib.ContentBlockHash;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Simple proxy backend transparently compressing/decompressing blocks on top of another backend
 */
public class GZipBackend implements IBlockStorageBackend
{
    static final String TARGET_ANNOTATION = "GZipTarget";

    private final IBlockStorageBackend _bsb;

    @Inject
    public GZipBackend(@Named(TARGET_ANNOTATION) IBlockStorageBackend bsb)
    {
        _bsb = bsb;
    }

    @Override
    public void init_() throws IOException
    {
        _bsb.init_();
    }

    @Override
    public InputStream getBlock(ContentBlockHash key) throws IOException
    {
        boolean ok = false;
        InputStream in = _bsb.getBlock(key);
        try {
            in = new GZIPInputStream(in);
            ok = true;
            return in;
        } finally {
            if (!ok) in.close();
        }
    }

    @Override
    public EncoderWrapping wrapForEncoding(OutputStream out) throws IOException
    {
        boolean ok = false;
        EncoderWrapping w = _bsb.wrapForEncoding(out);
        try {
            w = new EncoderWrapping(new GZIPOutputStream(w.wrapped), w.encoderData);
            ok = true;
            return w;
        } finally {
            if (!ok) out.close();
        }
    }

    @Override
    public void putBlock(ContentBlockHash key, InputStream input, long decodedLength,
            @Nullable Object encoderData) throws IOException
    {
        _bsb.putBlock(key, input, decodedLength, encoderData);
    }

    @Override
    public void deleteBlock(ContentBlockHash key, TokenWrapper tk) throws IOException
    {
        _bsb.deleteBlock(key, tk);
    }
}
