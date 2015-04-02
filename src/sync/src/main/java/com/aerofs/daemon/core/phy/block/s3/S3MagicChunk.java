/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.s3;

import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.TokenWrapper;
import com.aerofs.daemon.core.phy.block.encrypted.AbstractMagicChunk;

import com.aerofs.lib.SystemUtil.ExitCode;
import com.amazonaws.AmazonServiceException;

import java.io.EOFException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;

/**
 * S3 implementation of the MagicChunk
 */
class S3MagicChunk extends AbstractMagicChunk
{
    @Override
    public void init_mc(IBlockStorageBackend bsb) throws IOException
    {
        try {
            checkMagicChunk();
        } catch (AmazonServiceException e) {
            l.info("Got an AmazonServiceException, exiting with S3_BAD_CREDENTIALS: " + e.getMessage());
            if (e.getStatusCode() == 403 && "SignatureDoesNotMatch".equals(e.getErrorCode())) {
                ExitCode.S3_BAD_CREDENTIALS.exit();
            } else if (e.getStatusCode() == 403 && "InvalidAccessKeyId".equals(e.getErrorCode())) {
                ExitCode.S3_BAD_CREDENTIALS.exit();
            } else if (e.getStatusCode() == 404 && "NoSuchBucket".equals(e.getErrorCode())) {
                ExitCode.S3_BAD_CREDENTIALS.exit();
            } else {
                throw new IOException(e);
            }
        }
    }

    /**
     * Check/upload magic chunk.
     */
    protected void checkMagicChunk() throws IOException, AmazonServiceException
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
}
