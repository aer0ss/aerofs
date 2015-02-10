/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.rest.auth;


import com.aerofs.rest.api.Error;
import com.sun.jersey.api.core.HttpContext;
import org.jboss.netty.handler.codec.http.HttpHeaders;

import javax.annotation.Nullable;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public interface AuthTokenExtractor<T extends IAuthToken>
{
    public String challenge();

    public @Nullable T extract(HttpContext context);

    // A 401 for when the user fails to authorize correctly
    static WebApplicationException unauthorized(String message, String challenge)
    {
        return new WebApplicationException(Response
                .status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.Names.WWW_AUTHENTICATE, challenge)
                .header(HttpHeaders.Names.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .entity(new Error(Error.Type.UNAUTHORIZED, message))
                .build());
    }
}
