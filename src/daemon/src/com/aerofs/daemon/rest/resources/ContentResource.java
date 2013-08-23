/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;

@javax.ws.rs.Path("/0/content/{path: [^?]+}")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM})
public class ContentResource
{
    private final IIMCExecutor _imce;

    @Inject
    public ContentResource(CoreIMCExecutor imce)
    {
        _imce = imce.imce();
    }

    @GET
    public Response metadata(@PathParam("path") String path)
    {
        return Response.ok(new StreamingOutput() {
            @Override
            public void write(OutputStream outputStream)
                    throws IOException, WebApplicationException
            {
                outputStream.write("Hello\n".getBytes("UTF-8"));
            }
        }).type(MediaType.APPLICATION_OCTET_STREAM_TYPE).build();
    }
}
