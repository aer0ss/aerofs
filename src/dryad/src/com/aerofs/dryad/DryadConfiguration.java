/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad;

import com.aerofs.base.Loggers;
import com.aerofs.base.Version;
import com.aerofs.restless.Configuration;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class DryadConfiguration implements Configuration
{
    @Override
    public void addGlobalHeaders(HttpRequest request, HttpResponse response)
    {

    }

    @Override
    public Response resourceNotFound(String path)
    {
        Loggers.getLogger(DryadConfiguration.class).warn("resource not found: {}", path);
        return Response.status(Status.NOT_FOUND).build();
    }

    @Override
    public boolean isSupportedVersion(Version version)
    {
        return true;
    }
}
