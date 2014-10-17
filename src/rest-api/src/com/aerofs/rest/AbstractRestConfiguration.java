/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest;

import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.restless.Configuration;
import com.google.common.base.Joiner;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public abstract class AbstractRestConfiguration implements Configuration
{
    @Override
    public void addGlobalHeaders(HttpRequest request, HttpResponse response)
    {
        HttpHeaders h = response.headers();
        // inhibit caching by default
        if (!h.contains(Names.CACHE_CONTROL)) {
            h.set(Names.CACHE_CONTROL, getCacheControl(0));
        }

        /*
         * CORS: allow requests from any origin, allow any method, mark all request headers
         * as "allowed", and instruct the browser to expose all response headers.
         */
        h.set(Names.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        h.set(Names.ACCESS_CONTROL_ALLOW_METHODS, "GET,PUT,POST,PATCH,DELETE,HEAD,OPTIONS");
        h.set(Names.ACCESS_CONTROL_ALLOW_HEADERS, Joiner.on(',').join(request.headers().names()));
        h.set(Names.ACCESS_CONTROL_EXPOSE_HEADERS, Joiner.on(',').join(h.names()));
    }

    /**
     * Helper method to construct a cache-control header value given a max age in seconds.
     * If maxAge is zero, the reply will not be cached.
     */
    public static String getCacheControl(int maxAge)
    {
        return Values.PRIVATE  // This response is intended for a single user and must not be cached by a shared cache.
                + ", " + Values.NO_TRANSFORM  // Proxies should not alter the content
                + ", " + (maxAge > 0 ? Values.MAX_AGE + "=" + maxAge : Values.NO_CACHE);
    }

    @Override
    public Response resourceNotFound(String path)
    {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new Error(Type.NOT_FOUND, "No such resource"))
                .build();
    }
}
