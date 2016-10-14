package com.aerofs.daemon.rest;

import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.rest.handler.DaemonRestContentHelper;
import com.aerofs.daemon.rest.handler.RestContentHelper;
import com.aerofs.daemon.rest.resources.AbstractResource;
import com.aerofs.daemon.rest.resources.ChildrenResource;
import com.aerofs.daemon.rest.resources.FilesMetadataResource;
import com.aerofs.daemon.rest.resources.FoldersResource;
import com.aerofs.lib.guice.GuiceUtil;
import com.aerofs.restless.Configuration;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;

import static com.aerofs.lib.guice.GuiceUtil.multibind;

public class RestModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        GuiceUtil.multibind(binder(), ICoreEventHandlerRegistrar.class,
                RestCoreEventHandlerRegistar.class);

        bind(Configuration.class).to(RestConfiguration.class);
        multibind(binder(), AbstractResource.class, FoldersResource.class);
        multibind(binder(), AbstractResource.class, ChildrenResource.class);
        multibind(binder(), AbstractResource.class, FilesMetadataResource.class);
        bind(RestContentHelper.class).to(DaemonRestContentHelper.class);
    }
}
