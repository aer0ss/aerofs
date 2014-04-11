/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.base.Version;
import com.aerofs.restless.Configuration;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class AuditorConfiguration implements Configuration
{
    @Override
    public void addGlobalHeaders(HttpRequest request, HttpResponse response)
    {
    }

    @Override
    public Response resourceNotFound(String path)
    {
        return Response.status(Status.BAD_REQUEST).build();
    }

    @Override
    public boolean isSupportedVersion(Version version)
    {
        throw new UnsupportedOperationException();
    }
}
