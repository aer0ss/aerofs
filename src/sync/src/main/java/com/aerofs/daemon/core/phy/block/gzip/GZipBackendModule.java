/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.gzip;

import com.aerofs.daemon.core.phy.block.BlockStorageModules.AbstractProxyBackendModule;
import com.google.inject.name.Names;

import java.lang.annotation.Annotation;

/**
 * To enable compression in any backend simply add this module to the proxy chain
 * (see BlockStorageModules.proxy() for details)
 */
public class GZipBackendModule extends AbstractProxyBackendModule
{
    @Override
    protected void configure()
    {
        bindBackend().to(GZipBackend.class);
    }

    @Override
    public Annotation proxyTargetAnnotation()
    {
        return Names.named(GZipBackend.TARGET_ANNOTATION);
    }
}
