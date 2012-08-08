package com.aerofs.lib.aws.s3;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;

import org.apache.log4j.Logger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonServiceException.ErrorType;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectMetadataRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.io.ByteStreams;

import com.aerofs.lib.Base64;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LengthTrackingOutputStream;
import com.aerofs.lib.Util;

/**
 * A fake implementation of AbstractS3Client that stores objects in a directory
 * on disk.
 *
 * This class is designed to be used for unit tests. It currently doesn't store
 * any metadata or compute/check hashes, so if you need that functionality,
 * implement it yourself.
 */
public class FakeS3Client extends AbstractS3Client implements AmazonS3
{
    private static final Logger l = Util.l(FakeS3Client.class);

    private final File _rootDir;
    private final File _bucketsDir;
    private final File _tempDir;

    public FakeS3Client(File rootDir) throws IOException
    {
        _rootDir = rootDir.getCanonicalFile();
        _bucketsDir = new File(_rootDir, "buckets");
        FileUtil.ensureDirExists(_bucketsDir);
        _tempDir = new File(_rootDir, "tmp");
        FileUtil.ensureDirExists(_tempDir);
    }

    @Override
    public ObjectMetadata getObjectMetadata(GetObjectMetadataRequest request)
            throws AmazonClientException, AmazonServiceException
    {
        try {
            String bucketName = request.getBucketName();
            String key = request.getKey();

            File file = getPath(bucketName, key);
            if (!file.exists()) {
                throw notFoundException(bucketName, key);
            }

            ObjectMetadata metadata = readMetadata(file, new ObjectMetadata());
            return metadata;

        } catch (IOException e) {
            throw wrapException(e);
        }
    }

    @Override
    public S3Object getObject(String bucketName, String key)
            throws AmazonClientException, AmazonServiceException
    {
        return getObject(new GetObjectRequest(bucketName, key));
    }

    @Override
    public S3Object getObject(GetObjectRequest request)
            throws AmazonClientException, AmazonServiceException
    {
        try {
            String bucketName = request.getBucketName();
            String key = request.getKey();

            File file = getPath(bucketName, key);
            if (!file.exists()) {
                throw notFoundException(bucketName, key);
            }
            if (l.isDebugEnabled()) {
                l.debug("reading from " + file);
            }

            S3Object o = new S3Object();
            o.setBucketName(bucketName);
            o.setKey(key);
            readMetadata(file, o.getObjectMetadata());
            o.setObjectContent(new FileInputStream(file));
            return o;

        } catch (IOException e) {
            throw wrapException(e);
        }
    }

    @Override
    public PutObjectResult putObject(String bucketName, String key, File file)
            throws AmazonClientException, AmazonServiceException
    {
        return putObject(new PutObjectRequest(bucketName, key, file)
                .withMetadata(new ObjectMetadata()));
    }

    @Override
    public PutObjectResult putObject(String bucketName, String key, InputStream input,
            ObjectMetadata metadata)
            throws AmazonClientException, AmazonServiceException
    {
        return putObject(new PutObjectRequest(bucketName, key, input, metadata));
    }

    @SuppressWarnings("resource")
    @Override
    public PutObjectResult putObject(PutObjectRequest request)
            throws AmazonClientException, AmazonServiceException
    {
        try {
            String bucketName = request.getBucketName();
            String key = request.getKey();
            InputStream input = request.getInputStream();
            if (input == null) {
                File inputFile = request.getFile();
                input = new FileInputStream(inputFile);
            }

            try {
                File outputFile = getPath(bucketName, key);
                File metaFile = getMetaPath(outputFile);
                FileUtil.ensureDirExists(outputFile.getParentFile());
                FileUtil.ensureDirExists(metaFile.getParentFile());
                if (l.isDebugEnabled()) {
                    l.debug("writing to " + outputFile);
                }

                MessageDigest digest;
                try {
                    digest = MessageDigest.getInstance("MD5");
                } catch (NoSuchAlgorithmException e) {
                    throw new IOException(e);
                }

                File tempFile = null;
                File tempMetaFile = null;
                try {

                    long actualLength;
                    {
                        tempFile = File.createTempFile(outputFile.getName() + '-', ".tmp", _tempDir);
                        LengthTrackingOutputStream ltos;
                        OutputStream out = new FileOutputStream(tempFile);
                        try {
                            out = ltos = new LengthTrackingOutputStream(out);
                            out = new DigestOutputStream(out, digest);
                            out = new BufferedOutputStream(out);
                            ByteStreams.copy(input, out);
                        } finally {
                            out.close();
                        }
                        actualLength = ltos.getLength();
                    }

                    ObjectMetadata metadata = request.getMetadata();
                    PutObjectResult result = new PutObjectResult();

                    {
                        long expectedLength = metadata.getContentLength();
                        if (expectedLength != 0 && expectedLength != actualLength) {
                            l.debug("expected:" + expectedLength + " actual:" + actualLength);
                            AmazonServiceException e = new AmazonServiceException(
                                    "You did not provide the number of bytes specified by the Content-Length HTTP header.");
                            e.setErrorCode("IncompleteBody");
                            e.setErrorType(ErrorType.Client);
                            e.setStatusCode(400);
                            throw e;
                        }
                        if (expectedLength == 0) {
                            metadata.setContentLength(actualLength);
                        }

                        byte[] actualHash = digest.digest();
                        String md5Header = metadata.getContentMD5();
                        if (md5Header != null) {
                            byte[] expectedHash = Base64.decode(md5Header);
                            if (!Arrays.equals(expectedHash, actualHash)) {
                                AmazonServiceException e = new AmazonServiceException(
                                        "The Content-MD5 you specified did not match what we received.");
                                e.setErrorCode("BadDigest");
                                e.setErrorType(ErrorType.Client);
                                e.setStatusCode(400);
                                throw e;
                            }
                        } else {
                            metadata.setContentMD5(Base64.encodeBytes(actualHash));
                        }

                        tempMetaFile = File.createTempFile(metaFile.getName() + '-', ".tmp", _tempDir);
                        ObjectOutputStream out = new ObjectOutputStream(
                                new BufferedOutputStream(new FileOutputStream(tempMetaFile)));
                        try {
                            writeMetadata(out, metadata);
                        } finally {
                            out.close();
                        }
                    }

                    FileUtil.rename(tempFile, outputFile);
                    FileUtil.rename(tempMetaFile, metaFile);
                    tempFile = null;
                    tempMetaFile = null;

                    return result;
                } finally {
                    if (tempFile != null) tempFile.delete();
                    if (tempMetaFile != null) tempMetaFile.delete();
                }

            } finally {
                input.close();
            }
        } catch (IOException e) {
            throw wrapException(e);
        }
    }

    private void writeMetadata(ObjectOutput out, ObjectMetadata metadata) throws IOException
    {
        out.writeObject(metadata.getRawMetadata());
        out.writeObject(metadata.getUserMetadata());
    }

    private ObjectMetadata readMetadata(ObjectInput in, ObjectMetadata metadata) throws IOException
    {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawMetadata = (Map<String, Object>)in.readObject();
            @SuppressWarnings("unchecked")
            Map<String, String> userMetadata = (Map<String, String>)in.readObject();
            metadata.setUserMetadata(userMetadata);
            for (Map.Entry<String, Object> entry : rawMetadata.entrySet()) {
                metadata.setHeader(entry.getKey(), entry.getValue());
            }
            return metadata;
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    private ObjectMetadata readMetadata(File path, ObjectMetadata metadata) throws IOException
    {
        File metaFile = getMetaPath(path);
        ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(metaFile)));
        try {
            return readMetadata(in, metadata);
        } finally {
            in.close();
        }
    }

    private File getPath(String bucket, String key) throws IOException
    {
        if (bucket.indexOf('/') != -1 || bucket.indexOf('\\') != -1) {
            throw new AmazonServiceException("Bad bucket: " + bucket);
        }
        String saneBucket = sanitizeName(bucket);
        File bucketSubdir = new File(saneBucket);
        if (bucketSubdir.isAbsolute() || bucketSubdir.getParentFile() != null) {
            throw new AmazonServiceException("Bad bucket: " + bucket);
        }
        File baseDir = _bucketsDir.getCanonicalFile();
        File bucketDir = new File(baseDir, saneBucket).getCanonicalFile();
        if (!bucketDir.getParentFile().equals(baseDir)) {
            throw new AmazonServiceException("Bad bucket: " + bucket);
        }

        String newKey = sanitizeName(key);
        if (newKey.indexOf(File.separatorChar) != -1) {
            throw new AmazonServiceException("Bad key: " + key);
        }
        newKey += ".s3obj";
        File file = new File(bucketDir, newKey).getCanonicalFile();
        if (!file.getPath().startsWith(bucketDir.getPath())) {
            throw new AmazonServiceException("Bad key: " + key);
        }
        return file;
    }

    private File getMetaPath(File path) throws IOException
    {
        return new File(path.getParentFile(), path.getName() + ".meta");
    }

    private String sanitizeName(String name)
    {
        name = name.replace("~", "~t~");
        name = name.replace("/", "~s~");
        name = name.replace("\\", "~b~");
        return name;
    }

    private AmazonServiceException wrapException(Exception cause)
    {
        AmazonServiceException e = new AmazonServiceException("Internal Error: " + cause, cause);
        e.setErrorType(ErrorType.Service);
        e.setStatusCode(500);
        return e;
    }

    private AmazonServiceException notFoundException(String bucketName, String key)
    {
        AmazonServiceException e = new AmazonServiceException("File not found: " + key);
        e.setErrorType(ErrorType.Client);
        e.setErrorCode("NoSuchKey");
        e.setStatusCode(404);
        return e;
    }
}
