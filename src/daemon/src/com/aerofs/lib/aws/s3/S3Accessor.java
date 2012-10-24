package com.aerofs.lib.aws.s3;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;

import com.aerofs.lib.Util;
import com.aerofs.lib.aws.common.AWSRetry;
import com.aerofs.s3.S3Config.S3BucketIdConfig;

public class S3Accessor
{
    private static final Logger l = Util.l(S3Accessor.class);

    private final AmazonS3Client _s3;
    private final int ERROR_NOT_FOUND = 404;
    private final String _bucketId;

    @Inject
    public S3Accessor(AmazonS3Client client, S3BucketIdConfig s3BucketId)
    {
        this(client, s3BucketId.getS3BucketId());
    }

    public S3Accessor(AmazonS3Client client, String bucketid)
    {
        l.debug("Initialize S3 Credentials");

        _s3 = client;
        _bucketId = bucketid;

        try {
            //sanity testing:
            if (false == _s3.doesBucketExist(_bucketId)) Util.fatal("Bucket does not exist");

        } catch (AmazonClientException e) {
            Util.fatal(e);
        }
    }

    public boolean exists(final String key) throws IOException
    {
        l.debug("check if " + key + " exists ");

        return AWSRetry.retry(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    //will throw AmazonServiceException if object is not found
                    _s3.getObjectMetadata(_bucketId, key);
                    l.debug(key + " exists in s3");
                    return true;
                } catch (AmazonServiceException e)  {
                    if (ERROR_NOT_FOUND == e.getStatusCode()) {
                        l.debug(key + " DNE in s3");
                        return false;
                    } else {
                        l.warn(e);
                        throw e;
                    }
                }
            }
        });
    }

    public void upload(final String key, final File f) throws IOException
    {
        AWSRetry.retry(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
               l.debug("upload " + key);
               _s3.putObject(_bucketId, key, f);
               return null;
            }
        });
    }

    public void download(final String key, final File f) throws IOException
    {
        l.debug(" download " + key);
        assert exists(key);
        AWSRetry.retry(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                GetObjectRequest gor = new GetObjectRequest(_bucketId, key);
                _s3.getObject(gor,f);
                return null;
            }
        });
    }

    public void delete(final String key) throws IOException
    {
        l.debug(" delete " + key);
        AWSRetry.retry(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                _s3.deleteObject(_bucketId, key);
                return null;
            }
        });
    }

    //temporary function for migrating code from old S3 buckets to new S3 buckets
    public ObjectListing list() throws IOException
    {
        return AWSRetry.retry(new Callable<ObjectListing>() {
            @Override
            public ObjectListing call() throws Exception {
               return _s3.listObjects(_bucketId);
            }
        });
    }

    public ObjectListing listNextBatch(final ObjectListing ol) throws IOException
    {
        return AWSRetry.retry(new Callable<ObjectListing>() {
            @Override
            public ObjectListing call() throws Exception {
                return _s3.listNextBatchOfObjects(ol);

            }
        });
    }
}
