/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.rest.util;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.oauth.VerifyTokenResponse;
import com.aerofs.rest.api.Error.Type;
import com.google.inject.Inject;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.nio.channels.ClosedChannelException;
import java.util.List;

/**
 * Jersey container request filter that validates OAuth access tokens in HTTP requests and extracts
 * an {@link com.aerofs.rest.util.OAuthToken} to make it available through Context properties.
 *
 * The actual verification is done by {@link TokenVerifier}, which provides a cache atop of bifrost.
 */
public class OAuthRequestFilter implements ContainerRequestFilter
{
    private final static Logger l = Loggers.getLogger(OAuthRequestFilter.class);

    public final static String OAUTH_TOKEN = "oauth-token";

    private final TokenVerifier _verifier;

    @Inject
    public OAuthRequestFilter(TokenVerifier verifier)
    {
        _verifier = verifier;
    }

    @Override
    public ContainerRequest filter(ContainerRequest req)
    {
        String authHeader = singleton(req.getRequestHeader(HttpHeaders.AUTHORIZATION));
        String queryToken = singleton(req.getQueryParameters().get("token"));

        try {
            if ((authHeader == null) == (queryToken == null)) throw new Exception("invalid or missing oauth token");
            VerifyTokenResponse r = authHeader != null ?
                    _verifier.verifyHeader(authHeader) : _verifier.verifyToken(queryToken);
            if (r != null && r.principal != null) {
                l.info("verified");
                req.getProperties().put(OAUTH_TOKEN, new OAuthToken(r));
                return req;
            }
        } catch (Exception e) {
            l.error("failed to verify token", BaseLogUtil.suppress(e, ClosedChannelException.class));
        }

        throw new WebApplicationException(Response
                .status(Status.UNAUTHORIZED)
                .header(Names.WWW_AUTHENTICATE, "Bearer realm=\"AeroFS\"")
                .header(Names.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .entity(new com.aerofs.rest.api.Error(Type.UNAUTHORIZED, "Missing or invalid access token"))
                .build());
    }

    private @Nullable String singleton(List<String> l)
    {
        return l != null && l.size() == 1 ? l.get(0) : null;
    }
}
