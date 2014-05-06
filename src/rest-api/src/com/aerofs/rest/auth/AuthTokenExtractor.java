/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.rest.auth;


import com.sun.jersey.api.core.HttpContext;

import javax.annotation.Nullable;

public interface AuthTokenExtractor<T extends IAuthToken>
{
    public String challenge();

    public @Nullable T extract(HttpContext context);
}
