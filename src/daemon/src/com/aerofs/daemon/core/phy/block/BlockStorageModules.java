/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.lib.db.ISchema;
import com.aerofs.daemon.core.phy.ILinker;
import com.aerofs.daemon.core.phy.ILinker.NullLinker;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.Scoping;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;

import static com.aerofs.lib.guice.GuiceUtil.multibind;

import java.lang.annotation.Annotation;

/**
 * Base class for block-based storage modules
 *
 * This class is abstract on purpose to enforce specific instanciation of a backend-specific module.
 */
public class BlockStorageModules
{
    /**
     * To allow arbitrary chaining of proxy backends despite Guice stubborn refusal to let me
     * implement any sort of generic proxy binding we use an explicit annotation chain. Each proxy
     * backend that wants to participate in chaining must use it's own unique "proxy target"
     * annotation (see CacheBackend for an exmple).
     */
    public static abstract class AbstractBackendModule extends AbstractModule
    {
        private Annotation _annotation;

        public final void setBackendAnnotation(Annotation backendAnnotation)
        {
            _annotation = backendAnnotation;
        }

        protected final LinkedBindingBuilder<IBlockStorageBackend> bindBackend()
        {
            AnnotatedBindingBuilder<IBlockStorageBackend> b = bind(IBlockStorageBackend.class);
            return _annotation != null ? b.annotatedWith(_annotation) : b;
        }
    }

    public static abstract class AbstractProxyBackendModule extends AbstractBackendModule
    {
        public abstract Annotation proxyTargetAnnotation();
    }

    /**
     * Create a Module with arbitrary chaining of proxy backends on top of a basic storage backend
     *
     * proxy(new S3BackendModule(), new CacheBackendModule())
     *
     * will proxy the S3 backend through a cache
     *
     * hypothetical
     *
     * proxy(new GDriveBackendModule(),
     *       new CompressionBackendModule(), new CacheBackendModule(), new EncryptedBackendModule())
     *
     * would proxy Google Drive backend, firsth throug compression, then a cache and finally
     * encryption (i.e. data would still be encrypted but no longer compressed in the cache)
     */
    public static Module proxy(AbstractBackendModule base, AbstractProxyBackendModule... proxies)
    {
        AbstractBackendModule prev = base;
        for (AbstractProxyBackendModule proxy : proxies) {
            prev.setBackendAnnotation(proxy.proxyTargetAnnotation());
            prev = proxy;
        }

        return Modules.combine(storage(base), Modules.combine(proxies));
    }

    /**
     * Create a Module for a basic storage backend
     */
    public static Module storage(AbstractBackendModule base)
    {
        return Modules.combine(new AbstractModule() {
            @Override
            protected void configure()
            {
                bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

                bind(IPhysicalStorage.class).to(BlockStorage.class);
                bind(ILinker.class).to(NullLinker.class);

                // make sure the storage-specific schema is created on setup
                multibind(binder(), ISchema.class, BlockStorageSchema.class);

                Multibinder.newSetBinder(binder(), IBlockStorageInitable.class);
            }
        }, base);
    }
}