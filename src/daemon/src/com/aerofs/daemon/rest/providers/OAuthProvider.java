package com.aerofs.daemon.rest.providers;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.restless.Auth;
import com.google.inject.Inject;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.model.Parameter;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.server.impl.inject.AbstractHttpContextInjectable;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;
import org.slf4j.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.nio.channels.ClosedChannelException;

/**
 * Jersey injectable provider that validates OAuth access tokens in HTTP requests and extracts an
 * {@link AuthenticatedPrincipal} to make it available as an @Auth-annotated parameter to resource
 * methods.
 *
 * The actual verification is done by {@link TokenVerifier}, which provides a cache on top of
 * bifrost.
 */
public class OAuthProvider
        extends AbstractHttpContextInjectable<AuthenticatedPrincipal>
        implements InjectableProvider<Auth, Parameter>
{
    private final static Logger l = Loggers.getLogger(OAuthProvider.class);

    private final TokenVerifier _verifier;

    @Inject
    public OAuthProvider(TokenVerifier verifier)
    {
        _verifier = verifier;
    }

    @Override
    public AuthenticatedPrincipal getValue(HttpContext context)
    {
        String accessToken = context.getRequest().getQueryParameters().getFirst("access_token");
        try {
            if (accessToken != null) return _verifier.verify(accessToken).principal;
        } catch (Exception e) {
            l.error("failed to verify token", BaseLogUtil.suppress(e,
                    ClosedChannelException.class));
        }
        throw new WebApplicationException(Response
                .status(Status.UNAUTHORIZED)
                .entity(new Error(Type.UNAUTHORIZED, "Missing or invalid access_token"))
                .build());
    }

    @Override
    public ComponentScope getScope()
    {
        return ComponentScope.PerRequest;
    }

    @Override
    public Injectable<AuthenticatedPrincipal> getInjectable(ComponentContext ctx, Auth auth, Parameter param)
    {
        return param.getParameterClass().isAssignableFrom(AuthenticatedPrincipal.class) ? this : null;
    }
}
