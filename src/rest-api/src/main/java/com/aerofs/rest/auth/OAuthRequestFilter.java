/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.rest.auth;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.oauth.TokenVerifier;
import com.aerofs.oauth.VerifyTokenResponse;
import com.google.inject.Inject;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.ws.rs.core.HttpHeaders;
import java.nio.channels.ClosedChannelException;
import java.util.List;

/**
 * Jersey container request filter that validates OAuth access tokens in HTTP requests and extracts
 * an {@link OAuthToken} to make it available through Context properties.
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

        if ((authHeader == null) == (queryToken == null)) return req;

        try {
            VerifyTokenResponse r = authHeader != null ?
                    _verifier.verifyHeader(authHeader) : _verifier.verifyToken(queryToken);
            if (r != null && r.principal != null) {
                l.info("verified");
                req.getProperties().put(OAUTH_TOKEN, new OAuthToken(r));
            }
        } catch (Exception e) {
            l.error("failed to verify token", BaseLogUtil.suppress(e, ClosedChannelException.class));
        }
        return req;
    }

    private static @Nullable String singleton(List<String> l)
    {
        return l != null  && l.size() == 1 ? l.get(0) : null;
    }
}
