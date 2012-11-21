/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.s3;

import com.aerofs.daemon.core.phy.block.BlockStorageModules.AbstractBackendModule;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.s3.S3Config.S3EncryptionPasswordConfig;
import com.aerofs.s3.S3Config.S3EncryptionPasswordConfig.S3EncryptionPasswordFromDB;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class S3BackendModule extends AbstractBackendModule
{
    @Override
    protected void configure()
    {
        bindBackend().to(S3Backend.class).in(Scopes.SINGLETON);
    }

    @Provides
    public AWSCredentials provideAWSCredentials(CfgDatabase db)
    {
        return new BasicAWSCredentials(
                db.get(CfgDatabase.Key.S3_ACCESS_KEY),
                db.get(CfgDatabase.Key.S3_SECRET_KEY));
    }

    @Provides
    public S3EncryptionPasswordConfig provideS3EncryptionPasswordConfig(CfgDatabase db)
    {
        return new S3EncryptionPasswordFromDB(db);
    }

    @Provides
    public AmazonS3 provideS3Client(AWSCredentials creds)
    {
        return new AmazonS3Client(creds);
    }
}
