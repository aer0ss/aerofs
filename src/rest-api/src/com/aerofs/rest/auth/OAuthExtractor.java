package com.aerofs.rest.auth;

import com.sun.jersey.api.core.HttpContext;

/**
 * Jersey injectable provider that makes {@link OAuthToken} available as
 * an @Auth-annotated parameter to resource methods.
 *
 * The actual verification is done in {@link OAuthRequestFilter}
 */
public final class OAuthExtractor implements AuthTokenExtractor<OAuthToken>
{
    @Override
    public String challenge()
    {
        return "Bearer realm=\"AeroFS\"";
    }

    @Override
    public OAuthToken extract(HttpContext context)
    {
        Object token = context.getProperties().get(OAuthRequestFilter.OAUTH_TOKEN);
        if (token != null && token instanceof OAuthToken) {
            return (OAuthToken)token;
        }
        return null;
    }
}
