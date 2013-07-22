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
            // TODO: support other storage types?
            setup.setupSingleuser(
                    model.getClient(),
                    model.getUserID(),
                    model.getScrypted(),
                    model._localOptions._rootAnchorPath,
                    model.getDeviceName(),
                    StorageType.LINKED, null);
        }
    }

    public static class MultiUser extends InstallActor
    {
        @Override
        public void install(Setup setup, SetupModel model)
                throws Exception
        {
            // TODO: a little refactoring will get rid of this if/else block. Note few dissimilarities
            if (model._isLocal) {
                setup.setupMultiuser(
                        model.getClient(),
                        model.getUserID(),
                        model._localOptions._rootAnchorPath,
                        model.getDeviceName(),
                        model._localOptions._useBlockStorage ? StorageType.LOCAL : StorageType.LINKED,
                        null);
            } else {
                setup.setupMultiuser(
                        model.getClient(),
                        model.getUserID(),
                        setup.getDefaultAnchorRoot(),
                        model.getDeviceName(),
                        StorageType.S3,
                        model._s3Options.getConfig());
            }
        }
    }
}