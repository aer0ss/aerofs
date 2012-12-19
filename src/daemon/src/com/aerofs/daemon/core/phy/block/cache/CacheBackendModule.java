/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.cache;

import com.aerofs.daemon.core.phy.block.BlockStorageModules.AbstractProxyBackendModule;
import com.aerofs.daemon.lib.db.ISchema;

import static com.aerofs.lib.guice.GuiceUtil.multibind;

import com.google.inject.name.Names;

import java.lang.annotation.Annotation;

/**
 * To enable caching in any backend simply add this module to the proxy chain
 * (see BlockStorageModules.proxy() for details)
 */
public class CacheBackendModule extends AbstractProxyBackendModule
{
    @Override
    public void configure()
    {
        // make sure the storage-specific schema is created on setup
        multibind(binder(), ISchema.class, CacheSchema.class);

        bindBackend().to(CacheBackend.class);
    }

    @Override
    public Annotation proxyTargetAnnotation()
    {
        return Names.named(CacheBackend.TARGET_ANNOTATION);
    }
}
