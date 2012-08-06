package com.aerofs.lib.aws.s3.chunks;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.log4j.Logger;

import com.amazonaws.AmazonServiceException;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import com.aerofs.daemon.lib.HashStream;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.aws.s3.S3CredentialsException;
import com.aerofs.lib.aws.s3.S3InitException;
import com.aerofs.lib.aws.s3.chunks.S3ChunkAccessor.FileUpload;

class S3MagicChunk
{
    private static final Logger l = Util.l(S3MagicChunk.class);

    private final S3ChunkAccessor _s3ChunkAccessor;

    S3MagicChunk(S3ChunkAccessor s3ChunkAccessor)
    {
        _s3ChunkAccessor = s3ChunkAccessor;
    }

    public void init_() throws IOException, S3InitException
    {
        try {
            checkMagicChunk();
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 403) {
                if ("SignatureDoesNotMatch".equals(e.getErrorCode())) {
                    throw new S3CredentialsException(e);
                }
            }
            throw new IOException(e);
        }
    }


    /**
     * Check/upload magic chunk.
     *
     * The magic chunk is just an encoded empty chunk that's used to check that the client
     * has access to the S3 bucket and that the S3 encryption password is correct. If the chunk
     * doesn't exist yet it will be uploaded.
     */
    private void checkMagicChunk() throws IOException, AmazonServiceException
    {
        byte[] magic = {};

        try {
            downloadMagicChunk(magic);
            return;
        } catch (IOException e) {
            AmazonServiceException cause = getCauseOfClass(e, AmazonServiceException.class);
            if (cause == null) {
                throw e;
            } else if ("NoSuchKey".equals(cause.getErrorCode())) {
                // continue
            } else {
                throw cause;
            }
        }

        try {
            uploadMagicChunk(magic);
            downloadMagicChunk(magic);
        } catch (IOException e) {
            AmazonServiceException cause = getCauseOfClass(e, AmazonServiceException.class);
            if (cause == null) throw e;
            throw cause;
        }
    }

    private static <T extends Throwable> T getCauseOfClass(Throwable t, Class<T> cls) {
        while (t != null) {
            if (cls.isInstance(t)) return cls.cast(t);
            t = t.getCause();
        }
        return null;
    }

    private void downloadMagicChunk(byte[] magic) throws IOException
    {
        HashStream hs = HashStream.newFileHasher();
        hs.update(magic, 0, magic.length);
        hs.close();
        ContentHash hash = hs.getHashAttrib();
        byte[] bytes;
        InputStream in = _s3ChunkAccessor.readChunks(hash);
        try {
            bytes = ByteStreams.toByteArray(in);
        } finally {
            in.close();
        }

        if (!Arrays.equals(magic, bytes)) {
            throw new IOException("Incorrect magic chunk");
        }
    }

    private void uploadMagicChunk(byte[] magic) throws IOException
    {
        InputSupplier<? extends InputStream> input =
                ByteStreams.newInputStreamSupplier(magic);
        long length = magic.length;
        ListeningExecutorService executor = MoreExecutors.sameThreadExecutor();
        try {
            FileUpload upload = new FileUpload(_s3ChunkAccessor, executor, null, null, input, length);
            upload.setSkipEmpty(false);
            ContentHash hash = upload.uploadChunks();
            l.info("magic hash: " + hash);
        } finally {
            executor.shutdown();
        }
    }
}
