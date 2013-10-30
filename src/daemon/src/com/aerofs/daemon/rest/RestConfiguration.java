/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest;

import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.restless.Configuration;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.HttpResponse;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

public class RestConfiguration implements Configuration
{
    @Override
    public void addGlobalHeaders(HttpResponse response)
    {
        // Cross-Origin Resource Sharing
        response.setHeader(Names.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.setHeader(Names.CACHE_CONTROL, Values.NO_CACHE + "," + Values.NO_TRANSFORM);
    }

    @Override
    public Response URINotFound(URI uri)
    {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new Error(Type.NOT_FOUND, "No such resource"))
                .build();
    }
}
