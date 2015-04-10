package com.aerofs.daemon.core.phy.block.swift;


import com.aerofs.daemon.core.phy.block.BlockStorageModules.AbstractBackendModule;
import com.aerofs.daemon.core.phy.block.IBlockStorageInitable;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.guice.GuiceUtil;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import org.javaswift.joss.client.factory.AuthenticationMethod;

public class SwiftBackendModule extends AbstractBackendModule
{
    @Override
    protected void configure()
    {
        bindBackend().to(SwiftBackend.class).in(Scopes.SINGLETON);

        GuiceUtil.multibind(binder(), IBlockStorageInitable.class, SwiftMagicChunk.class);
    }

    @Provides
    public SwiftConfig.EncryptionPasswordConfig provideS3EncryptionPasswordConfig(CfgDatabase db)
    {
        return new SwiftConfig.EncryptionPasswordConfig.EncryptionPasswordFromDB(db);
    }

    @Provides
    public SwiftConfig provideSwiftConfig(CfgDatabase db)
    {
        AuthenticationMethod authMethod;

        switch (db.get(CfgDatabase.Key.SWIFT_AUTHMODE)) {
            case "basic":
                authMethod = AuthenticationMethod.BASIC;
                break;
            case "keystone":
                authMethod = AuthenticationMethod.KEYSTONE;
                break;
            default:
                throw new IllegalStateException("Unsupported Swift auth mode (only supports 'basic' and 'keystone')");
        }

        return new SwiftConfig(
            db.get(CfgDatabase.Key.SWIFT_USERNAME),
            db.get(CfgDatabase.Key.SWIFT_PASSWORD),
            db.get(CfgDatabase.Key.SWIFT_URL),
            authMethod,
            db.get(CfgDatabase.Key.SWIFT_CONTAINER),
            db.getNullable(CfgDatabase.Key.SWIFT_TENANT_ID),
            db.getNullable(CfgDatabase.Key.SWIFT_TENANT_NAME)
        );
    }
}
