/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.providers;

import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.rest.auth.AuthTokenExtractor;
import com.aerofs.rest.auth.IAuthToken;
import com.aerofs.restless.Auth;
import com.google.common.collect.ImmutableList;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.model.Parameter;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.server.impl.inject.AbstractHttpContextInjectable;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import java.util.List;
import java.util.stream.Collectors;

import static com.aerofs.base.GenericUtils.getTypeInterface;

/**
 * Auth provider that can accept a variety of credentials
 */
public class AuthProvider implements InjectableProvider<Auth, Parameter>
{
    private final static Logger l = LoggerFactory.getLogger(AuthProvider.class);

    private final ImmutableList<AuthTokenExtractor<? extends IAuthToken>> _providers;

    @SafeVarargs
    @SuppressWarnings("varargs")
    public AuthProvider(AuthTokenExtractor<? extends IAuthToken> ...providers)
    {
        _providers = ImmutableList.copyOf(providers);
    }

    @Override
    public ComponentScope getScope()
    {
        return ComponentScope.PerRequest;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Injectable<IAuthToken> getInjectable(ComponentContext ctx, Auth auth, Parameter param)
    {
        // TODO: cache by param class?
        List<AuthTokenExtractor<? extends IAuthToken>> l = _providers.stream()
                .filter(p -> match(p.getClass(), param))
                .collect(Collectors.<AuthTokenExtractor<? extends IAuthToken>>toList());

        if (l.size() == 0) return null;
        return new InjectableAuth(l);
    }

    private static class InjectableAuth extends AbstractHttpContextInjectable<IAuthToken>
    {
        private final List<AuthTokenExtractor<? extends IAuthToken>> _providers;

        private InjectableAuth(List<AuthTokenExtractor<? extends IAuthToken>> providers)
        {
            _providers = providers;
        }

        @Override
        public IAuthToken getValue(HttpContext context)
        {
            for (AuthTokenExtractor<? extends IAuthToken> p : _providers) {
                IAuthToken t = p.extract(context);
                if (t != null) return t;
            }
            // If none of the providers returned successfully, and none threw, fall back to generic
            // "not authorized" message
            l.error("missing or invalid token");
            // see RFC 7235: https://tools.ietf.org/html/rfc7235#section-4.1
            throw unauthorized(_providers.stream()
                    .map(AuthTokenExtractor::challenge)
                    .collect(Collectors.joining(", ")));
        }
    }

    private static boolean match(Class<?> c, Parameter param)
    {
        return param.getParameterClass().isAssignableFrom(
                getTypeInterface(c, AuthTokenExtractor.class, 0));
    }

    private static WebApplicationException unauthorized(String challenge)
    {
        return new WebApplicationException(Response
                .status(Status.UNAUTHORIZED)
                .header(Names.WWW_AUTHENTICATE, challenge)
                .header(Names.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .entity(new Error(Type.UNAUTHORIZED, "No valid authentication provided"))
                .build());
    }
}