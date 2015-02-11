package com.aerofs.daemon.core.phy.block.s3;

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
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.aerofs.base.Loggers;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.S3ResponseMetadata;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.BucketCrossOriginConfiguration;
import com.amazonaws.services.s3.model.BucketLifecycleConfiguration;
import com.amazonaws.services.s3.model.BucketLoggingConfiguration;
import com.amazonaws.services.s3.model.BucketNotificationConfiguration;
import com.amazonaws.services.s3.model.BucketPolicy;
import com.amazonaws.services.s3.model.BucketTaggingConfiguration;
import com.amazonaws.services.s3.model.BucketVersioningConfiguration;
import com.amazonaws.services.s3.model.BucketWebsiteConfiguration;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.CopyObjectResult;
import com.amazonaws.services.s3.model.CopyPartRequest;
import com.amazonaws.services.s3.model.CopyPartResult;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.DeleteBucketCrossOriginConfigurationRequest;
import com.amazonaws.services.s3.model.DeleteBucketLifecycleConfigurationRequest;
import com.amazonaws.services.s3.model.DeleteBucketPolicyRequest;
import com.amazonaws.services.s3.model.DeleteBucketRequest;
import com.amazonaws.services.s3.model.DeleteBucketTaggingConfigurationRequest;
import com.amazonaws.services.s3.model.DeleteBucketWebsiteConfigurationRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import com.amazonaws.services.s3.model.DeleteVersionRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetBucketAclRequest;
import com.amazonaws.services.s3.model.GetBucketLocationRequest;
import com.amazonaws.services.s3.model.GetBucketPolicyRequest;
import com.amazonaws.services.s3.model.GetBucketWebsiteConfigurationRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ListBucketsRequest;
import com.amazonaws.services.s3.model.ListMultipartUploadsRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListPartsRequest;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.MultipartUploadListing;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.Owner;
import com.amazonaws.services.s3.model.PartListing;
import com.amazonaws.services.s3.model.Region;
import com.amazonaws.services.s3.model.RestoreObjectRequest;
import com.amazonaws.services.s3.model.SetBucketAclRequest;
import com.amazonaws.services.s3.model.SetBucketCrossOriginConfigurationRequest;
import com.amazonaws.services.s3.model.SetBucketLifecycleConfigurationRequest;
import com.amazonaws.services.s3.model.SetBucketLoggingConfigurationRequest;
import com.amazonaws.services.s3.model.SetBucketNotificationConfigurationRequest;
import com.amazonaws.services.s3.model.SetBucketPolicyRequest;
import com.amazonaws.services.s3.model.SetBucketTaggingConfigurationRequest;
import com.amazonaws.services.s3.model.SetBucketVersioningConfigurationRequest;
import com.amazonaws.services.s3.model.SetBucketWebsiteConfigurationRequest;
import com.amazonaws.services.s3.model.SetObjectAclRequest;
import com.amazonaws.services.s3.model.StorageClass;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;
import com.amazonaws.services.s3.model.VersionListing;
import org.slf4j.Logger;

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

import com.aerofs.base.Base64;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LengthTrackingOutputStream;

/**
 * A fake implementation of AbstractS3Client that stores objects in a directory
 * on disk.
 *
 * This class is designed to be used for unit tests. It currently doesn't store
 * any metadata or compute/check hashes, so if you need that functionality,
 * implement it yourself.
 */
public class FakeS3Client implements AmazonS3
{
    private static final Logger l = Loggers.getLogger(FakeS3Client.class);

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
            throws AmazonClientException
    {
        try {
            String bucketName = request.getBucketName();
            String key = request.getKey();

            File file = getPath(bucketName, key);
            if (!file.exists()) {
                throw notFoundException(bucketName, key);
            }

            return readMetadata(file, new ObjectMetadata());
        } catch (IOException e) {
            throw wrapException(e);
        }
    }

    @Override
    public S3Object getObject(String bucketName, String key) throws AmazonClientException
    {
        return getObject(new GetObjectRequest(bucketName, key));
    }

    @Override
    public S3Object getObject(GetObjectRequest request) throws AmazonClientException
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
            throws AmazonClientException
    {
        return putObject(new PutObjectRequest(bucketName, key, file)
                .withMetadata(new ObjectMetadata()));
    }

    @Override
    public PutObjectResult putObject(String bucketName, String key, InputStream input,
            ObjectMetadata metadata)
            throws AmazonClientException
    {
        return putObject(new PutObjectRequest(bucketName, key, input, metadata));
    }

    @Override
    @SuppressWarnings("resource")
    public PutObjectResult putObject(PutObjectRequest request) throws AmazonClientException
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

    @Override
    public void setEndpoint(String endpoint)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRegion(com.amazonaws.regions.Region region)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setS3ClientOptions(S3ClientOptions s3ClientOptions)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObjectAcl(SetObjectAclRequest request)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void
            changeObjectStorageClass(String bucketName, String key, StorageClass newStorageClass)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObjectRedirectLocation(String s, String s2, String s3)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectListing listObjects(String bucketName) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectListing listObjects(String bucketName, String prefix)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectListing listObjects(ListObjectsRequest listObjectsRequest)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectListing listNextBatchOfObjects(ObjectListing previousObjectListing)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionListing listVersions(String bucketName, String prefix)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionListing listNextBatchOfVersions(VersionListing previousVersionListing)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionListing listVersions(String bucketName, String prefix, String keyMarker,
            String versionIdMarker, String delimiter, Integer maxResults)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionListing listVersions(ListVersionsRequest listVersionsRequest)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Owner getS3AccountOwner() throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean doesBucketExist(String bucketName) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Bucket> listBuckets() throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Bucket> listBuckets(ListBucketsRequest listBucketsRequest)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getBucketLocation(String bucketName) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getBucketLocation(GetBucketLocationRequest getBucketLocationRequest)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bucket createBucket(CreateBucketRequest createBucketRequest)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bucket createBucket(String bucketName) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bucket createBucket(String bucketName, Region region) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bucket createBucket(String bucketName, String region) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccessControlList getObjectAcl(String bucketName, String key)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccessControlList getObjectAcl(String bucketName, String key, String versionId)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObjectAcl(String bucketName, String key, AccessControlList acl)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObjectAcl(String bucketName, String key, CannedAccessControlList acl)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void
            setObjectAcl(String bucketName, String key, String versionId, AccessControlList acl)
                    throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObjectAcl(String bucketName, String key, String versionId,
            CannedAccessControlList acl) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccessControlList getBucketAcl(String bucketName) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketAcl(SetBucketAclRequest setBucketAclRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccessControlList getBucketAcl(GetBucketAclRequest getBucketAclRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketAcl(String bucketName, AccessControlList acl)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketAcl(String bucketName, CannedAccessControlList acl)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectMetadata getObjectMetadata(String bucketName, String key)
            throws AmazonClientException
    {
        return getObjectMetadata(new GetObjectMetadataRequest(bucketName, key));
    }

    @Override
    public ObjectMetadata getObject(GetObjectRequest getObjectRequest, File destinationFile)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucket(DeleteBucketRequest deleteBucketRequest) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucket(String bucketName) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CopyObjectResult copyObject(String sourceBucketName, String sourceKey,
            String destinationBucketName, String destinationKey) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CopyObjectResult copyObject(CopyObjectRequest copyObjectRequest)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CopyPartResult copyPart(CopyPartRequest copyPartRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteObject(String bucketName, String key) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteObject(DeleteObjectRequest deleteObjectRequest) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteObjectsResult deleteObjects(DeleteObjectsRequest deleteObjectsRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteVersion(String bucketName, String key, String versionId)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteVersion(DeleteVersionRequest deleteVersionRequest)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketLoggingConfiguration getBucketLoggingConfiguration(String bucketName)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketLoggingConfiguration(
            SetBucketLoggingConfigurationRequest setBucketLoggingConfigurationRequest)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketVersioningConfiguration getBucketVersioningConfiguration(String bucketName)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketVersioningConfiguration(
            SetBucketVersioningConfigurationRequest setBucketVersioningConfigurationRequest)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketLifecycleConfiguration getBucketLifecycleConfiguration(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketLifecycleConfiguration(String s,
            BucketLifecycleConfiguration bucketLifecycleConfiguration)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketLifecycleConfiguration(
            SetBucketLifecycleConfigurationRequest setBucketLifecycleConfigurationRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucketLifecycleConfiguration(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucketLifecycleConfiguration(
            DeleteBucketLifecycleConfigurationRequest deleteBucketLifecycleConfigurationRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketCrossOriginConfiguration getBucketCrossOriginConfiguration(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketCrossOriginConfiguration(String s,
            BucketCrossOriginConfiguration bucketCrossOriginConfiguration)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketCrossOriginConfiguration(
            SetBucketCrossOriginConfigurationRequest setBucketCrossOriginConfigurationRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucketCrossOriginConfiguration(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucketCrossOriginConfiguration(
            DeleteBucketCrossOriginConfigurationRequest deleteBucketCrossOriginConfigurationRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketTaggingConfiguration getBucketTaggingConfiguration(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketTaggingConfiguration(String s,
            BucketTaggingConfiguration bucketTaggingConfiguration)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketTaggingConfiguration(
            SetBucketTaggingConfigurationRequest setBucketTaggingConfigurationRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucketTaggingConfiguration(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucketTaggingConfiguration(
            DeleteBucketTaggingConfigurationRequest deleteBucketTaggingConfigurationRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketNotificationConfiguration getBucketNotificationConfiguration(String bucketName)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketNotificationConfiguration(
            SetBucketNotificationConfigurationRequest setBucketNotificationConfigurationRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketNotificationConfiguration(String bucketName,
            BucketNotificationConfiguration bucketNotificationConfiguration)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketWebsiteConfiguration getBucketWebsiteConfiguration(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketWebsiteConfiguration getBucketWebsiteConfiguration(
            GetBucketWebsiteConfigurationRequest getBucketWebsiteConfigurationRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketWebsiteConfiguration(String s,
            BucketWebsiteConfiguration bucketWebsiteConfiguration)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketWebsiteConfiguration(
            SetBucketWebsiteConfigurationRequest setBucketWebsiteConfigurationRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucketWebsiteConfiguration(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucketWebsiteConfiguration(
            DeleteBucketWebsiteConfigurationRequest deleteBucketWebsiteConfigurationRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketPolicy getBucketPolicy(String bucketName) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketPolicy getBucketPolicy(GetBucketPolicyRequest getBucketPolicyRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketPolicy(String bucketName, String policyText) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketPolicy(SetBucketPolicyRequest setBucketPolicyRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucketPolicy(String bucketName) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucketPolicy(DeleteBucketPolicyRequest deleteBucketPolicyRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public URL generatePresignedUrl(String bucketName, String key, Date expiration)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public URL generatePresignedUrl(String bucketName, String key, Date expiration,
            HttpMethod method) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public URL generatePresignedUrl(GeneratePresignedUrlRequest generatePresignedUrlRequest)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public InitiateMultipartUploadResult initiateMultipartUpload(
            InitiateMultipartUploadRequest request) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadPartResult uploadPart(UploadPartRequest request) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PartListing listParts(ListPartsRequest request) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void abortMultipartUpload(AbortMultipartUploadRequest request)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompleteMultipartUploadResult completeMultipartUpload(
            CompleteMultipartUploadRequest request) throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public MultipartUploadListing listMultipartUploads(ListMultipartUploadsRequest request)
            throws AmazonClientException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public S3ResponseMetadata getCachedResponseMetadata(AmazonWebServiceRequest request)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void restoreObject(RestoreObjectRequest restoreObjectRequest)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void restoreObject(String s, String s2, int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enableRequesterPays(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void disableRequesterPays(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRequesterPaysEnabled(String s)
    {
        throw new UnsupportedOperationException();
    }
}
