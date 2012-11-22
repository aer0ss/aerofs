package com.aerofs.daemon.core.phy.block.s3;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.mockito.Mockito;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

import com.aerofs.daemon.core.phy.block.s3.S3Config.S3BucketIdConfig;
import com.aerofs.daemon.core.phy.block.s3.S3Config.S3CryptoConfig;
import com.aerofs.daemon.core.phy.block.s3.S3Config.S3EncryptionPasswordConfig;
import com.aerofs.testlib.UnitTestTempDir;

public class S3TestConfig
{
    private final boolean _useRealS3Client = false;

    private final AWSCredentials _awsCredentials =
            new BasicAWSCredentials(
                    "AKIAIFL3D777LIQS6TRA",
                    "l/l6f2A+PSOVPaNV9J75gjLg2ApSvVRGOyCO6Tj5");

    public AWSCredentials getAWSCredentials()
    {
        return _awsCredentials;
    }

    private S3BucketIdConfig _s3BucketIdConfig;
    {
        _s3BucketIdConfig = Mockito.mock(S3BucketIdConfig.class);
        Mockito.when(_s3BucketIdConfig.getS3BucketId()).thenReturn("aerofs.test");
        String path = "unittest/junit-" + System.getProperty("user.name");
        if (_useRealS3Client) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS");
            path += "/" + sdf.format(new Date());
        }
        Mockito.when(_s3BucketIdConfig.getS3DirPath()).thenReturn(path);
    }

    public S3BucketIdConfig getBucketIdConfig()
    {
        return _s3BucketIdConfig;
    }

    private S3EncryptionPasswordConfig _s3EncryptionPasswordConfig =
            new S3EncryptionPasswordConfig() {
        @Override
        public char[] getPassword()
        {
            return "password".toCharArray();
        }
    };

    public S3EncryptionPasswordConfig getS3EncryptionPasswordConfig()
    {
        return _s3EncryptionPasswordConfig;
    }

    private S3CryptoConfig _s3CryptoConfig;
    {
        _s3CryptoConfig = new S3CryptoConfig(_s3EncryptionPasswordConfig);
    }

    public S3CryptoConfig getS3CryptoConfig()
    {
        return _s3CryptoConfig;
    }

    private AmazonS3 _s3Client;

    public AmazonS3 getS3Client(UnitTestTempDir tempDirFactory) throws IOException
    {
        if (_s3Client == null) {
            if (_useRealS3Client) {
                _s3Client = new AmazonS3Client(getAWSCredentials());
            } else {
                File testDir = tempDirFactory.getTestTempDir();
                _s3Client = new FakeS3Client(new File(testDir, "fakes3"));
            }
        }
        return _s3Client;
    }
}
