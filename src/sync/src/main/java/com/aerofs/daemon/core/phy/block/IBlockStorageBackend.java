/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.injectable.InjectableFile;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Backend performing the actual block storage
 *
 * The storage backend implement a very basic key,value store. Values are arbitrary blocks of binary
 * data and keys are some unspecified cryptographic hash of the values (most likely SHA-256 but not
 * guaranteed by this interface).
 *
 * Remote backends should be proxied through a cache to reduce latency
 */
public interface IBlockStorageBackend
{
    void init_() throws IOException;

    /**
     * Read a block from the storage backend
     */
    InputStream getBlock(ContentBlockHash key) throws IOException;

    /**
     * Write a block to the storage backend
     *
     * NOTE: the input stream is guaranteed to be repeatable, i.e calling reset() will bring the
     * position back to the start of the block without any loss.
     */
    void putBlock(ContentBlockHash key, InputStream input, long decodedLength) throws IOException;

    public interface TokenWrapper
    {
        void pseudoPause(String reason) throws ExAborted;
        void pseudoResumed() throws ExAborted;
    }

    /**
     * Remove a block from the storage backend
     */
    void deleteBlock(ContentBlockHash key, TokenWrapper tk) throws IOException;
}
