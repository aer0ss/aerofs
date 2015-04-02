/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.encrypted;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.block.BlockInputStream;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.daemon.core.phy.block.IBlockStorageInitable;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.SystemUtil;
import com.google.common.io.ByteStreams;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Arrays;

/**
 * The magic chunk is just an encoded empty chunk that's used to check that the client
 * has access to the storage backend (and bucket/container) and that the encryption password is correct. If the chunk
 * doesn't exist yet it will be uploaded.
 *
 * Because the storage backend might be wrapped by arbitray proxy backends, this class cannot simply
 * be owned and initialized by the EncryptedBackend itself. Instead it needs to use the outermost
 * backend in the proxy chain. Doing this without creating a cyclic dependency in Guice requires
 * some creativity. The approach taken was to use multibind to add arbitrary (unordered) init_ tasks
 * to BlockStorage through the IBlockStorageInitable interface.
 */
public abstract class AbstractMagicChunk implements IBlockStorageInitable
{
    protected static final Logger l = Loggers.getLogger(AbstractMagicChunk.class);

    protected IBlockStorageBackend _bsb;

    protected static final byte[] MAGIC = {};
    protected static final ContentBlockHash MAGIC_HASH
            = new ContentBlockHash(BaseSecUtil.hash(MAGIC));

    @Override
    public void init_(IBlockStorageBackend bsb) throws IOException
    {
        _bsb = bsb;

        try {
            init_mc(bsb);
        } catch (IOException e) {
            if (getCauseOfClass(e, InvalidKeyException.class) != null) {
                /** InvalidKeyException can be thrown from
                 * {@link com.aerofs.lib.SecUtil.CipherFactory#newEncryptingCipher()}, if Java has
                 * a restricted key length. See other call sites of the exit code for more info.
                 * See support-182 for the full error stack.
                 */
                SystemUtil.ExitCode.STORAGE_JAVA_KEY_LENGTH_MAYBE_TOO_LIMITED.exit();
            } else if (getCauseOfClass(e, URISyntaxException.class) != null) {
                // This can happen when there are illegal characters such as spaces in the bucket
                // name, because the bucket name will be used as the hostname part of a URL.
                SystemUtil.ExitCode.REMOTE_STORAGE_INVALID_CONFIG.exit();
            } else {
                throw e;
            }
        }
    }

    public abstract void init_mc(IBlockStorageBackend bsb) throws IOException;

    /**
     * Check/upload magic chunk.
     */
    protected abstract void checkMagicChunk() throws IOException;

    protected static @Nullable
    <T extends Throwable> T getCauseOfClass(Throwable t, Class<T> cls)
    {
        while (t != null) {
            if (cls.isInstance(t)) return cls.cast(t);
            t = t.getCause();
        }
        return null;
    }

    protected void downloadMagicChunk() throws IOException
    {
        byte[] bytes;
        try (InputStream in = new BlockInputStream(_bsb, MAGIC_HASH)) {
            bytes = ByteStreams.toByteArray(in);
        }

        if (!Arrays.equals(MAGIC, bytes)) {
            throw new IOException("Incorrect magic chunk: expected "
                    + BaseUtil.hexEncode(MAGIC) + " actual "
                    + BaseUtil.hexEncode(bytes));
        }
    }

    protected void uploadMagicChunk() throws IOException
    {
        _bsb.putBlock(MAGIC_HASH, new ByteArrayInputStream(MAGIC), MAGIC.length);
    }
}
