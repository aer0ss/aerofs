/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.s3;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.phy.block.BlockInputStream;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.TokenWrapper;
import com.aerofs.daemon.core.phy.block.IBlockStorageInitable;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.amazonaws.AmazonServiceException;
import com.google.common.io.ByteStreams;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Arrays;

/**
 * The magic chunk is just an encoded empty chunk that's used to check that the client
 * has access to the S3 bucket and that the S3 encryption password is correct. If the chunk
 * doesn't exist yet it will be uploaded.
 *
 * Because the S3 backend might be wrapped by arbitray proxy backends, this class cannot simply
 * be owned and initialized by the S3Backend itself. Instead it needs to use the outermost
 * backend in the proxy chain. Doing this without creating a cyclic dependency in Guice requires
 * some creativity. The approach taken was to use multibind to add arbitrary (unordered) init_ tasks
 * to BlockStorage through the IBlockStorageInitable interface.
 */
class S3MagicChunk implements IBlockStorageInitable
{
    private static final Logger l = Loggers.getLogger(S3MagicChunk.class);

    private IBlockStorageBackend _bsb;

    private static final byte[] MAGIC = {};
    private static final ContentBlockHash MAGIC_HASH
            = new ContentBlockHash(BaseSecUtil.hash(MAGIC));

    @Override
    public void init_(IBlockStorageBackend bsb) throws IOException
    {
        _bsb = bsb;

        try {
            checkMagicChunk();
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 403 && "SignatureDoesNotMatch".equals(e.getErrorCode())) {
                ExitCode.S3_BAD_CREDENTIALS.exit();
            } else if (e.getStatusCode() == 403 && "InvalidAccessKeyId".equals(e.getErrorCode())) {
                ExitCode.S3_BAD_CREDENTIALS.exit();
            } else if (e.getStatusCode() == 404 && "NoSuchBucket".equals(e.getErrorCode())) {
                ExitCode.S3_BAD_CREDENTIALS.exit();
            } else {
                throw new IOException(e);
            }
        } catch (IOException e) {
            if (getCauseOfClass(e, InvalidKeyException.class) != null) {
                /** InvalidKeyException can be thrown from
                 * {@link com.aerofs.lib.SecUtil.CipherFactory#newEncryptingCipher()}, if Java has
                 * a restricted key length. See other call sites of the exit code for more info.
                 * See support-182 for the full error stack.
                 */
                ExitCode.S3_JAVA_KEY_LENGTH_MAYBE_TOO_LIMITED.exit();
            } else if (getCauseOfClass(e, URISyntaxException.class) != null) {
                // This can happen when there are illegal characters such as spaces in the bucket
                // name, because the bucket name will be used as the hostname part of a URL.
                ExitCode.S3_BAD_CREDENTIALS.exit();
            } else {
                throw e;
            }
        }
    }

    /**
     * Check/upload magic chunk.
     */
    private void checkMagicChunk() throws IOException, AmazonServiceException
    {
        try {
            downloadMagicChunk();
            return;
        } catch (EOFException e) {
            // one-time magic chunk fix
            // A refactor of the S3 backend broke the magic chunk logic a while back but it
            // went unnoticed because the exception was erroneously swallowed until another
            // refactor changed that
            // The problem was that the magic chunk is simply a regular (empty) chunk and
            // should therefore be stored/fetched using the whole backend proxy chain but
            // which did not matter as long as encryption and compression where performed
            // by the S3Backend itself but when GZipBackend was pulled out to be reused by
            // LocalBackend this class was not updated and still used the S3Backend directly
            // which broke the encoding of new magic chunks and decoding of old ones.
            // "New" (i.e uncompressed) magic chunks created during between that change and
            // this fix therefore need to be deleted and re-uploaded
            l.info("magic chunk fix");
            _bsb.deleteBlock(MAGIC_HASH, new TokenWrapper() {
                @Override
                public void pseudoPause(String reason) throws ExAborted {}

                @Override
                public void pseudoResumed() throws ExAborted {}
            });
        } catch (IOException e) {
            AmazonServiceException cause = getCauseOfClass(e, AmazonServiceException.class);
            if (cause == null) {
                throw e;
            } else if (cause.getStatusCode() == 404 || "NoSuchKey".equals(cause.getErrorCode())) {
                // continue
            } else {
                throw cause;
            }
        }

        try {
            uploadMagicChunk();
            downloadMagicChunk();
        } catch (IOException e) {
            AmazonServiceException cause = getCauseOfClass(e, AmazonServiceException.class);
            if (cause == null) throw e;
            throw cause;
        }
    }

    private static @Nullable
    <T extends Throwable> T getCauseOfClass(Throwable t, Class<T> cls)
    {
        while (t != null) {
            if (cls.isInstance(t)) return cls.cast(t);
            t = t.getCause();
        }
        return null;
    }

    private void downloadMagicChunk() throws IOException
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

    private void uploadMagicChunk() throws IOException
    {
        _bsb.putBlock(MAGIC_HASH, new ByteArrayInputStream(MAGIC), MAGIC.length);
    }
}