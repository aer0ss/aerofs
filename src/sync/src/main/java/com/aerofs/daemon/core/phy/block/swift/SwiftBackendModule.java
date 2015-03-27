package com.aerofs.daemon.core.phy.block.swift;


import com.aerofs.daemon.core.phy.block.BlockStorageModules.AbstractBackendModule;
import com.aerofs.daemon.core.phy.block.IBlockStorageInitable;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.ICfgStore;
import com.aerofs.lib.guice.GuiceUtil;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import org.javaswift.joss.client.factory.AuthenticationMethod;

import static com.aerofs.lib.cfg.ICfgStore.*;

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
    public SwiftConfig provideSwiftConfig(ICfgStore store)
    {
        AuthenticationMethod authMethod;

        switch (store.get(SWIFT_AUTHMODE)) {
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
            store.get(SWIFT_USERNAME),
            store.get(SWIFT_PASSWORD),
            store.get(SWIFT_URL),
            authMethod,
            store.get(SWIFT_CONTAINER),
            store.get(SWIFT_TENANT_ID),
            store.get(SWIFT_TENANT_NAME)
        );
    }
}
