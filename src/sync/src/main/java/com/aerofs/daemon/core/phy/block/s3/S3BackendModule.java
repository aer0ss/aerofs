/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.block.s3;

import com.aerofs.daemon.core.phy.block.BlockStorageModules.AbstractBackendModule;
import com.aerofs.daemon.core.phy.block.IBlockStorageInitable;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.daemon.core.phy.block.s3.S3Config.S3EncryptionPasswordConfig;
import com.aerofs.daemon.core.phy.block.s3.S3Config.S3EncryptionPasswordConfig.S3EncryptionPasswordFromDB;
import com.aerofs.lib.guice.GuiceUtil;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.google.inject.Provides;
import com.google.inject.Scopes;

import javax.annotation.Nullable;

public class S3BackendModule extends AbstractBackendModule
{
    @Override
    protected void configure()
    {
        bindBackend().to(S3Backend.class).in(Scopes.SINGLETON);

        GuiceUtil.multibind(binder(), IBlockStorageInitable.class, S3MagicChunk.class);
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
    public AmazonS3 provideS3Client(AWSCredentials creds, CfgDatabase db)
    {
        AmazonS3 s3 = new AmazonS3Client(creds);

        // Use path-style (http://host.com/bucket) rather than virtual-host-style
        // (http://bucket.host.com) for privately deployed S3 compatible systems
        // e.g. OpenStack Swift.
        s3.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true));

        // Null testing is for old Team Servers that don't have the endpoint field populated.
        // The default end point is S3 US East region.
        @Nullable String endpoint = db.getNullable(CfgDatabase.Key.S3_ENDPOINT);
        if (endpoint != null) s3.setEndpoint(endpoint);
        return s3;
    }
}
