package com.aerofs.daemon.rest.netty;

import com.sun.jersey.api.container.ContainerException;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.ContainerProvider;
import com.sun.jersey.spi.container.WebApplication;

/**
 * This class is referenced by META-INF/services/com.sun.jersey.spi.container.ContainerProvider file
 * TODO (GS): Obfuscation will probably break this
 */
public class NettyContainerProvider implements ContainerProvider<JerseyHandler>
{
    @Override
    public JerseyHandler createContainer(final Class<JerseyHandler> type,
            final ResourceConfig resourceConfig, final WebApplication application)
            throws ContainerException
    {
        JerseyHandler handler = null;
        if (type == JerseyHandler.class) {
            handler = new JerseyHandler(application);
        }
        return handler;
    }
}
