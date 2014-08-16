/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.restless.providers;

import com.aerofs.restless.Version;
import com.aerofs.restless.jersey.VersionFilterFactory;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.model.Parameter;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.server.impl.inject.AbstractHttpContextInjectable;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;

import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

/**
 * Easy access to Version object extracted by VersionfilterFactory in resource method:
 *
 * {@code
 * @GET
 * void foo(@Context Version version) {
 * }
 * }
 */
@Provider
public class VersionProvider extends AbstractHttpContextInjectable<Version>
        implements InjectableProvider<Context, Parameter>
{
    @Override
    public Version getValue(HttpContext context)
    {
        return (Version)context.getProperties().get(VersionFilterFactory.REQUEST_VERSION);
    }

    @Override
    public ComponentScope getScope()
    {
        return ComponentScope.PerRequest;
    }

    @Override
    public Injectable<Version> getInjectable(ComponentContext ctx, Context context, Parameter param)
    {
        return param.getParameterClass().isAssignableFrom(Version.class) ? this : null;
    }
}
