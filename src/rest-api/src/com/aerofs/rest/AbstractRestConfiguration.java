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
        if (!h.contains(Names.CACHE_CONTROL)) {
            // If the response is successful, cache for 3 minutes, otherwise do not cache
            int code = response.getStatus().getCode();
            int maxAge = (code >= 200 && code < 300) ? 3 * 60 : 0;
            h.set(Names.CACHE_CONTROL, getCacheControl(maxAge));
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
    protected static String getCacheControl(int maxAge)
    {
        String result = Values.PRIVATE  // This response is intended for a single user and must not be cached by a shared cache.
                + ", " + Values.NO_TRANSFORM  // Proxies should not alter the content
                + ", " + Values.MAX_AGE + "=" + maxAge;

        if (maxAge == 0) result += ", " + Values.NO_CACHE;

        return result;
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
