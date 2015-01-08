package com.aerofs.rest.providers;

import com.aerofs.base.Loggers;
import com.aerofs.rest.api.Error.Type;
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
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.slf4j.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

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
    private final static Logger l = Loggers.getLogger(OAuthProvider.class);

    @Override
    public OAuthToken getValue(HttpContext context)
    {
        Object token = context.getProperties().get(OAuthRequestFilter.OAUTH_TOKEN);
        if (token != null && token instanceof OAuthToken) {
            return (OAuthToken)token;
        }
        l.error("missing or invalid token");
        throw new WebApplicationException(Response
                .status(Status.UNAUTHORIZED)
                .header(Names.WWW_AUTHENTICATE, "Bearer realm=\"AeroFS\"")
                .header(Names.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .entity(new com.aerofs.rest.api.Error(Type.UNAUTHORIZED, "Missing or invalid access token"))
                .build());
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
