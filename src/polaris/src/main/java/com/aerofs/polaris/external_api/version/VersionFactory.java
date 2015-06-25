package com.aerofs.polaris.external_api.version;

import com.aerofs.polaris.external_api.rest.util.Version;
import org.glassfish.hk2.api.Factory;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;

import static com.aerofs.polaris.external_api.Constants.REQUEST_VERSION;

public class VersionFactory implements Factory<Version>
{
    private final ContainerRequestContext context;

    @Inject
    public VersionFactory(ContainerRequestContext context)
    {
        this.context = context;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Version provide()
    {
        if (context.getProperty(REQUEST_VERSION) instanceof Version) {
            return (Version)(context.getProperty(REQUEST_VERSION));
        }
        return null;
    }

    @Override
    public void dispose(Version instance)
    {
    }
}
