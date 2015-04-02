package com.aerofs.controller;

import com.aerofs.lib.StorageType;

/**
 * Actors that perform installation steps after the initial signin.
 */
public abstract class InstallActor
{
    abstract void install(Setup setup, SetupModel model) throws Exception;

    public static class SingleUser extends InstallActor
    {
        @Override
        void install(Setup setup, SetupModel model) throws Exception
        {
            setup.setupSingleuser(
                    model.getClient(),
                    model.getUserID(),
                    model.getScrypted(),
                    model._localOptions._rootAnchorPath,
                    model.getDeviceName(),
                    StorageType.LINKED, null,
                    model.isAPIAccessEnabled());
        }
    }

    public static class MultiUser extends InstallActor
    {
        @Override
        public void install(Setup setup, SetupModel model)
                throws Exception
        {
            setup.setupMultiuser(
                    model.getClient(),
                    model.getUserID(),
                    model._isLocal ? model._localOptions._rootAnchorPath : Setup.getDefaultAnchorRoot(),
                    model.getDeviceName(),
                    model._isLocal
                            ? model._localOptions._useBlockStorage
                                    ? StorageType.LOCAL
                                    : StorageType.LINKED
                            : model._backendConfig._storageType,
                    model._isLocal ? null : model._backendConfig,
                    model.isAPIAccessEnabled()
            );
       }
    }
}
