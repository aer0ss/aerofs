/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.providers;

import com.aerofs.rest.providers.OAuthProvider;
import com.aerofs.rest.util.OAuthToken;
import com.google.inject.Inject;
import com.sun.jersey.api.core.HttpContext;

/**
 * Auth provider that accepts either an Oauth token or a device cert (through nginx)
 */
public class AuthProvider extends OAuthProvider
{
    @Inject
    public AuthProvider()
    {
    }

    @Override
    public OAuthToken getValue(HttpContext context)
    {
        // TODO: client cert verification (ideally reuse HttpRequestAuthenticator from auditor)

        return super.getValue(context);
    }
}
