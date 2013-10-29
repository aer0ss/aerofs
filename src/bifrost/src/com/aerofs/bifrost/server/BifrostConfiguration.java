/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.restless.Configuration;
import org.jboss.netty.handler.codec.http.HttpResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.net.URI;

public class BifrostConfiguration implements Configuration
{
    @Override
    public void addGlobalHeaders(HttpResponse response)
    {

    }

    @Override
    public Response URINotFound(URI uri)
    {
        return Response.status(Status.BAD_REQUEST).build();
    }
}
