/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.s3;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.Base64;
import com.aerofs.ids.UniqueID;
import com.aerofs.daemon.core.phy.block.encrypted.EncryptedBackend;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LengthTrackingOutputStream;
import com.aerofs.base.BaseSecUtil.CipherFactory;
import com.aerofs.daemon.core.phy.block.s3.S3Config.S3BucketIdConfig;
import com.aerofs.daemon.core.ex.ExAborted;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.*;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.google.common.base.Preconditions.checkState;

/**
 * BlockStorage backend based on Amazon S3
 */
public class S3Backend extends EncryptedBackend
{
    private static final Logger l = Loggers.getLogger(S3Backend.class);

    private final AmazonS3 _s3Client;
    private final S3BucketIdConfig _s3BucketIdConfig;

    @Inject
    public S3Backend(AmazonS3 s3Client, S3BucketIdConfig s3BucketIdConfig,
            S3Config.CryptoConfig cryptoConfig)
    {
        super(cryptoConfig);
        _s3Client = s3Client;
        _s3BucketIdConfig = s3BucketIdConfig;
    }

    @Override
    public InputStream getBlock(ContentBlockHash k) throws IOException
    {
        String key = getBlockKey(k.toHex());
        S3Object o;
        try {
            o = _s3Client.getObject(_s3BucketIdConfig.getS3BucketId(), key);
        } catch (AmazonClientException e) {
            throw new IOException(e);
        }
        return wrapForDecoding(o.getObjectContent());
    }

    /**
     * We need to keep track of length and MD5 of the encoded (compressed+encrypted) data
     * for use in the S3 call.
     */
    private static class EncoderData
    {
        byte[] md5;
        long encodedLength;
    }

    /**
     * Allows backends to perform arbitrary data encoding through wrapper output streams and pass
     * any computation result from such wrapper streams (e.g. MD5 hash of encoded data) to putBlock
     */
    static class EncoderWrapping
    {
        public final OutputStream wrapped;
        public final EncoderData encoderData;

        public EncoderWrapping(OutputStream out, EncoderData data)
        {
            wrapped = out;
            encoderData = data;
        }
    }
    /**
     * Encrypt blocks before storing remotely
     *
     * Also need to compute MD5 of the encoded data to comply with S3 API
     */
    public EncoderWrapping wrapForEncoding(OutputStream out) throws IOException
    {
        final EncoderData d = new EncoderData();

        boolean ok = false;
        try {
            final MessageDigest md;
            try {
                // compute the MD5 hash of the compressed, encrypted data for Amazon S3
                md = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IOException(e);
            }

            out = new LengthTrackingOutputStream(out) {
                @Override
                public void close() throws IOException
                {
                    super.close();
                    d.encodedLength = getLength();
                    d.md5 = md.digest();
                }
            };
            out = new DigestOutputStream(out, md);
            out = new BufferedOutputStream(out);
            out = new CipherFactory(_secretKey).encryptingHmacedOutputStream(out);
            ok = true;
            return new EncoderWrapping(out, d);
        } finally {
            if (!ok) out.close();
        }
    }

    private static class EncodingBuffer implements AutoCloseable
    {
        private static final int IN_MEMORY_THRESHOLD = 64 * C.KB;

        long length;
        byte[] mem;

        @Nullable File f;

        EncodingBuffer()
        {
            mem = new byte[IN_MEMORY_THRESHOLD];
        }

        long encodedLength()
        {
            if (f != null) {
                checkState(mem.length + f.length() == length,
                        "corrupted encoding buffer: %s + %s != %s", mem.length, f.length(), length);
            }
            return length;
        }

        ByteSource encoded()
        {
            if (f == null) return ByteSource.wrap(mem).slice(0, length);
            return ByteSource.concat(ByteSource.wrap(mem), Files.asByteSource(f));
        }

        OutputStream encodingStream() {
            return new OutputStream() {
                FileOutputStream out;

                @Override
                public void write(int b) throws IOException {
                    if (length < mem.length) {
                        mem[(int)length] = (byte)b;
                        ++length;
                        return;
                    }

                    if (f == null) spill();
                    out.write(b);
                    ++length;
                }

                @Override
                public void write(byte b[], int off, int len) throws IOException {
                    if (length < mem.length) {
                        int n = Math.min(len, mem.length - (int)length);
                        System.arraycopy(b, off, mem, (int)length, n);
                        length += n;
                        off += n;
                        len -= n;
                    }
                    if (len == 0) return;
                    if (f == null) spill();
                    out.write(b, off, len);
                    length += len;
                }

                private void spill() throws IOException
                {
                    f = FileUtil.createTempFile("encbuf" + UniqueID.generate().toStringFormal(),
                            null, null);
                    out = new FileOutputStream(f);
                }

                @Override
                public void close() throws IOException {
                    if (out != null) out.close();
                }
            };
        }

        @Override
        public void close() throws IOException {
            if (f != null) FileUtil.delete(f);
            mem = null;
        }
    }

    @Override
    public void putBlock(final ContentBlockHash key, final InputStream input, final long decodedLength)
            throws IOException
    {
        try (EncodingBuffer buffer = new EncodingBuffer()) {
            l.debug("s3 encoding {} {}", key, decodedLength);
            final EncoderWrapping w = wrapForEncoding(buffer.encodingStream());
            try (OutputStream out = w.wrapped) {
                ByteStreams.copy(input, out);
            }
            l.debug("s3 encoding done");

            String baseKey = key.toHex();
            String s3Key = getBlockKey(baseKey);
            String bucketName = _s3BucketIdConfig.getS3BucketId();

            try {
                ObjectMetadata metadata = _s3Client.getObjectMetadata(bucketName, s3Key);
                l.debug("md:{}", metadata);
                return;
            } catch (AmazonServiceException e) {
                l.debug("404 when trying to get S3 object metadata", BaseLogUtil.suppress(e));
            }

            EncoderData d = w.encoderData;
            checkState(d.encodedLength == buffer.encodedLength(),
                    "inconsistent encoded length: %s != %s", d.encodedLength, buffer.encodedLength());

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("application/octet-stream");
            metadata.setContentLength(d.encodedLength);
            metadata.setContentMD5(Base64.encodeBytes(d.md5));
            metadata.addUserMetadata("Chunk-Length", Long.toString(decodedLength));
            metadata.addUserMetadata("Chunk-Hash", baseKey);
            l.debug("s3 upload {}", key);
            try (InputStream in = buffer.encoded().openStream()) {
                _s3Client.putObject(bucketName, s3Key, in, metadata);
                l.debug("s3 upload done {}", key);
            } catch (AmazonServiceException e) {
                throw new IOException(e);
            }
        }
    }

    @Override
    public void deleteBlock(final ContentBlockHash key, TokenWrapper tk) throws IOException
    {
        try {
            tk.pseudoPause("s3-del");
            try {
                String baseKey = key.toHex();
                String s3Key = getBlockKey(baseKey);
                String bucketName = _s3BucketIdConfig.getS3BucketId();
                _s3Client.deleteObject(bucketName, s3Key);
            } catch (AmazonServiceException e) {
                throw new IOException(e);
            } finally {
                tk.pseudoResumed();
            }
        } catch (ExAborted e) {
            throw new IOException(e);
        }
    }

    private String getBlockKeyPrefix()
    {
        String prefix = _s3BucketIdConfig.getS3DirPath();
        if (!prefix.isEmpty() && prefix.charAt(prefix.length() - 1) != '/') prefix += '/';
        return prefix + BLOCK_DIR;
    }

    private String getBlockKey(String baseKey)
    {
        return getBlockKeyPrefix() + baseKey + BLOCK_SUFFIX;
    }
}
