/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.s3;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.base.Base64;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.LengthTrackingOutputStream;
import com.aerofs.base.BaseSecUtil.CipherFactory;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.daemon.core.phy.block.s3.S3Config.S3BucketIdConfig;
import com.aerofs.daemon.core.phy.block.s3.S3Config.S3CryptoConfig;
import com.aerofs.daemon.core.ex.ExAborted;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.crypto.SecretKey;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.Callable;

/**
 * BlockStorage backend based on Amazon S3
 *
 * The blocks are transparently encrypted locally before on the way to remote storage and
 * transparently decrypted on the way back.
 */
public class S3Backend implements IBlockStorageBackend
{
    private static final Logger l = Loggers.getLogger(S3Backend.class);

    private static final String BLOCK_SUFFIX = ".chunk.gz.aes";

    private final AmazonS3 _s3Client;
    private final S3BucketIdConfig _s3BucketIdConfig;
    private final S3CryptoConfig _s3CryptoConfig;

    private SecretKey _secretKey;

    @Inject
    public S3Backend(AmazonS3 s3Client, S3BucketIdConfig s3BucketIdConfig,
            S3CryptoConfig s3CryptoConfig)
    {
        _s3Client = s3Client;
        _s3BucketIdConfig = s3BucketIdConfig;
        _s3CryptoConfig = s3CryptoConfig;
    }

    @Override
    public void init_() throws IOException
    {
        try {
            _secretKey = _s3CryptoConfig.getSecretKey();
        } catch (NoSuchAlgorithmException e) {
            ExitCode.S3_BAD_CREDENTIALS.exit();
        } catch (InvalidKeySpecException e) {
            ExitCode.S3_BAD_CREDENTIALS.exit();
        }
    }

    @Override
    public InputStream getBlock(ContentHash k) throws IOException
    {
        String key = getBlockKey(k.toHex());
        S3Object o;
        try {
            o = _s3Client.getObject(_s3BucketIdConfig.getS3BucketId(), key);
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        } catch (AmazonClientException e) {
            throw new IOException(e);
        }
        return wrapForDecoding(o.getObjectContent());
    }

    private InputStream wrapForDecoding(InputStream in) throws IOException
    {
        boolean ok = false;
        try {
            in = new CipherFactory(_secretKey).decryptingHmacedInputStream(in);
            ok = true;
            return in;
        } finally {
            if (!ok) in.close();
        }
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
     * Encrypt blocks before storing remotely
     *
     * Also need to compute MD5 of the encoded data to comply with S3 API
     */
    @Override
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

    @Override
    public void putBlock(final ContentHash key, final InputStream input, final long decodedLength,
            final Object encoderData) throws IOException
    {
        AWSRetry.retry(new Callable<Void>()
        {
            @Override
            public Void call() throws IOException
            {
                String baseKey = key.toHex();
                String s3Key = getBlockKey(baseKey);
                String bucketName = _s3BucketIdConfig.getS3BucketId();

                try {
                    ObjectMetadata metadata = _s3Client.getObjectMetadata(bucketName, s3Key);
                    l.debug("md:{}", metadata);
                    return null;
                } catch (AmazonServiceException e) {
                    l.debug(Util.e(e));
                }

                EncoderData d = (EncoderData)encoderData;

                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType("application/octet-stream");
                metadata.setContentLength(d.encodedLength);
                metadata.setContentMD5(Base64.encodeBytes(d.md5));
                metadata.addUserMetadata("Chunk-Length", Long.toString(decodedLength));
                metadata.addUserMetadata("Chunk-Hash", baseKey);
                try {
                    _s3Client.putObject(bucketName, s3Key, input, metadata);
                } finally {
                    // caller guarantees that calling reset will bring the input stream back to the
                    // start of the block without losing any data
                    input.reset();
                }
                return null;
            }
        });
    }

    @Override
    public void deleteBlock(final ContentHash key, TokenWrapper tk) throws IOException
    {
        try {
            tk.pseudoPause("s3-del");
            try {
                AWSRetry.retry(new Callable<Void>()
                {
                    @Override
                    public Void call() throws Exception
                    {
                        String baseKey = key.toHex();
                        String s3Key = getBlockKey(baseKey);
                        String bucketName = _s3BucketIdConfig.getS3BucketId();

                        _s3Client.deleteObject(bucketName, s3Key);
                        return null;
                    }
                });
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
        return prefix + "chunks/";
    }

    private String getBlockKey(String baseKey)
    {
        return getBlockKeyPrefix() + baseKey + BLOCK_SUFFIX;
    }
}
