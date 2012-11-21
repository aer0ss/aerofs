package com.aerofs.s3;

import com.aerofs.daemon.lib.db.ISchema;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

import com.aerofs.daemon.core.linker.ILinker;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.s3.S3Storage;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.s3.S3Config.S3EncryptionPasswordConfig;
import com.aerofs.s3.S3Config.S3EncryptionPasswordConfig.S3EncryptionPasswordFromDB;

import static com.aerofs.lib.guice.GuiceUtil.multiBind;

public class S3Module extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(IPhysicalStorage.class).to(S3Storage.class).in(Scopes.SINGLETON);
        bind(ILinker.class).to(ILinker.NullLinker.class).in(Scopes.SINGLETON);

        // make sure the storage-specific schema is created on setup
        multiBind(binder(), ISchema.class, S3Schema.class);
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
