/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad;

import com.aerofs.restless.Version;
import com.aerofs.restless.Configuration;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class DryadConfiguration implements Configuration
{
    private static final Logger l = LoggerFactory.getLogger(DryadConfiguration.class);

    @Override
    public void addGlobalHeaders(HttpRequest request, HttpResponse response)
    {
    }

    @Override
    public Response resourceNotFound(String path)
    {
        l.warn("resource not found: {}", path);
        return Response.status(Status.NOT_FOUND).build();
    }

    @Override
    public boolean isSupportedVersion(Version version)
    {
        return true;
    }
}
