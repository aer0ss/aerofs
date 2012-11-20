/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.lib.db.ISchema;
import com.aerofs.daemon.core.linker.ILinker;
import com.aerofs.daemon.core.linker.ILinker.NullLinker;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

/**
 * Base class for block-based storage modules
 *
 * This class is abstract on purpose to enforce specific instanciation of a backend-specific module.
 */
public abstract class AbstractBlockStorageModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(IPhysicalStorage.class).to(BlockStorage.class).in(Scopes.SINGLETON);
        bind(ILinker.class).to(NullLinker.class).in(Scopes.SINGLETON);

        // make sure the storage-specific schema is created on setup
        Multibinder.newSetBinder(binder(), ISchema.class).addBinding().to(BlockStorageSchema.class);

        configureBackend();
    }

    /**
     * Backend-specific configuration
     */
    protected abstract void configureBackend();
}