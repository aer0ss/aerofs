package com.aerofs.lib.aws.s3;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.S3ResponseMetadata;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.BucketLoggingConfiguration;
import com.amazonaws.services.s3.model.BucketNotificationConfiguration;
import com.amazonaws.services.s3.model.BucketPolicy;
import com.amazonaws.services.s3.model.BucketVersioningConfiguration;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.CopyObjectResult;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.DeleteBucketRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.DeleteVersionRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetBucketLocationRequest;
import com.amazonaws.services.s3.model.GetObjectMetadataRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ListBucketsRequest;
import com.amazonaws.services.s3.model.ListMultipartUploadsRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListPartsRequest;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.MultipartUploadListing;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.Owner;
import com.amazonaws.services.s3.model.PartListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.Region;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.SetBucketLoggingConfigurationRequest;
import com.amazonaws.services.s3.model.SetBucketVersioningConfigurationRequest;
import com.amazonaws.services.s3.model.StorageClass;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;
import com.amazonaws.services.s3.model.VersionListing;

/**
 * An abstraction of the AWS S3 client interface that can be faked as needed.
 */
abstract class AbstractS3Client implements AmazonS3
{
    public S3Object getObject(String bucketName, String key)
            throws AmazonClientException, AmazonServiceException
    {
        return getObject(new GetObjectRequest(bucketName, key));
    }

    public abstract S3Object getObject(GetObjectRequest getObjectRequest)
            throws AmazonClientException, AmazonServiceException;

    public PutObjectResult putObject(String bucketName, String key, File file)
            throws AmazonClientException, AmazonServiceException
    {
        return putObject(new PutObjectRequest(bucketName, key, file)
                .withMetadata(new ObjectMetadata()));
    }

    public PutObjectResult putObject(String bucketName, String key, InputStream input,
            ObjectMetadata metadata)
            throws AmazonClientException, AmazonServiceException
    {
        return putObject(new PutObjectRequest(bucketName, key, input, metadata));
    }

    public abstract PutObjectResult putObject(PutObjectRequest putObjectRequest)
            throws AmazonClientException, AmazonServiceException;

    @Override
    public void setEndpoint(String endpoint)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void
            changeObjectStorageClass(String bucketName, String key, StorageClass newStorageClass)
                    throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectListing listObjects(String bucketName) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectListing listObjects(String bucketName, String prefix)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectListing listObjects(ListObjectsRequest listObjectsRequest)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectListing listNextBatchOfObjects(ObjectListing previousObjectListing)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionListing listVersions(String bucketName, String prefix)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionListing listNextBatchOfVersions(VersionListing previousVersionListing)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionListing listVersions(String bucketName, String prefix, String keyMarker,
            String versionIdMarker, String delimiter, Integer maxResults)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionListing listVersions(ListVersionsRequest listVersionsRequest)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Owner getS3AccountOwner() throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean doesBucketExist(String bucketName) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Bucket> listBuckets() throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Bucket> listBuckets(ListBucketsRequest listBucketsRequest)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getBucketLocation(String bucketName) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getBucketLocation(GetBucketLocationRequest getBucketLocationRequest)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bucket createBucket(CreateBucketRequest createBucketRequest)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bucket createBucket(String bucketName) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bucket createBucket(String bucketName, Region region) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bucket createBucket(String bucketName, String region) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccessControlList getObjectAcl(String bucketName, String key)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccessControlList getObjectAcl(String bucketName, String key, String versionId)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObjectAcl(String bucketName, String key, AccessControlList acl)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObjectAcl(String bucketName, String key, CannedAccessControlList acl)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void
            setObjectAcl(String bucketName, String key, String versionId, AccessControlList acl)
                    throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObjectAcl(String bucketName, String key, String versionId,
            CannedAccessControlList acl) throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccessControlList getBucketAcl(String bucketName) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketAcl(String bucketName, AccessControlList acl)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketAcl(String bucketName, CannedAccessControlList acl)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectMetadata getObjectMetadata(String bucketName, String key)
            throws AmazonClientException, AmazonServiceException
    {
        return getObjectMetadata(new GetObjectMetadataRequest(bucketName, key));
    }

    @Override
    public ObjectMetadata getObjectMetadata(GetObjectMetadataRequest getObjectMetadataRequest)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectMetadata getObject(GetObjectRequest getObjectRequest, File destinationFile)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucket(DeleteBucketRequest deleteBucketRequest) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucket(String bucketName) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CopyObjectResult copyObject(String sourceBucketName, String sourceKey,
            String destinationBucketName, String destinationKey) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CopyObjectResult copyObject(CopyObjectRequest copyObjectRequest)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteObject(String bucketName, String key) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteObject(DeleteObjectRequest deleteObjectRequest) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteVersion(String bucketName, String key, String versionId)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteVersion(DeleteVersionRequest deleteVersionRequest)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketLoggingConfiguration getBucketLoggingConfiguration(String bucketName)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketLoggingConfiguration(
            SetBucketLoggingConfigurationRequest setBucketLoggingConfigurationRequest)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketVersioningConfiguration getBucketVersioningConfiguration(String bucketName)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketVersioningConfiguration(
            SetBucketVersioningConfigurationRequest setBucketVersioningConfigurationRequest)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketNotificationConfiguration getBucketNotificationConfiguration(String bucketName)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketNotificationConfiguration(String bucketName,
            BucketNotificationConfiguration bucketNotificationConfiguration)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BucketPolicy getBucketPolicy(String bucketName) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBucketPolicy(String bucketName, String policyText) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBucketPolicy(String bucketName) throws AmazonClientException,
            AmazonServiceException
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
            InitiateMultipartUploadRequest request) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadPartResult uploadPart(UploadPartRequest request) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PartListing listParts(ListPartsRequest request) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void abortMultipartUpload(AbortMultipartUploadRequest request)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompleteMultipartUploadResult completeMultipartUpload(
            CompleteMultipartUploadRequest request) throws AmazonClientException,
            AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public MultipartUploadListing listMultipartUploads(ListMultipartUploadsRequest request)
            throws AmazonClientException, AmazonServiceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public S3ResponseMetadata getCachedResponseMetadata(AmazonWebServiceRequest request)
    {
        throw new UnsupportedOperationException();
    }
}
