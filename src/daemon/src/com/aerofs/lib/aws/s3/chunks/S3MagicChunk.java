package com.aerofs.lib.aws.s3.chunks;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.util.Arrays;

import com.aerofs.lib.ExitCode;
import org.apache.log4j.Logger;

import com.amazonaws.AmazonServiceException;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import com.aerofs.daemon.lib.HashStream;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.aws.s3.chunks.S3ChunkAccessor.FileUpload;

import javax.annotation.Nullable;

class S3MagicChunk
{
    private static final Logger l = Util.l(S3MagicChunk.class);

    private final S3ChunkAccessor _s3ChunkAccessor;

    S3MagicChunk(S3ChunkAccessor s3ChunkAccessor)
    {
        _s3ChunkAccessor = s3ChunkAccessor;
    }

    public void init_() throws IOException
    {
        try {
            checkMagicChunk();
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 403 && "SignatureDoesNotMatch".equals(e.getErrorCode())) {
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
            }
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
            } else if (cause.getStatusCode() == 404 || "NoSuchKey".equals(cause.getErrorCode())) {
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

    private static @Nullable <T extends Throwable> T getCauseOfClass(Throwable t, Class<T> cls)
    {
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
