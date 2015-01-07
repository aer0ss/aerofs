package com.aerofs.rest.providers;

import com.aerofs.rest.util.OAuthRequestFilter;
import com.aerofs.rest.util.OAuthToken;
import com.aerofs.restless.Auth;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.model.Parameter;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.server.impl.inject.AbstractHttpContextInjectable;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;

/**
 * Jersey injectable provider that makes {@link com.aerofs.rest.util.OAuthToken} available as
 * an @Auth-annotated parameter to resource methods.
 *
 * The actual verification is done in {@link com.aerofs.rest.util.OAuthRequestFilter}
 */
public class OAuthProvider
        extends AbstractHttpContextInjectable<OAuthToken>
        implements InjectableProvider<Auth, Parameter>
{
    @Override
    public OAuthToken getValue(HttpContext context)
    {
        return (OAuthToken)context.getProperties().get(OAuthRequestFilter.OAUTH_TOKEN);
    }

    @Override
    public ComponentScope getScope()
    {
        return ComponentScope.PerRequest;
    }

    @Override
    public Injectable<OAuthToken> getInjectable(ComponentContext ctx, Auth auth, Parameter param)
    {
        return param.getParameterClass().isAssignableFrom(OAuthToken.class) ? this : null;
    }
}
