/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import com.aerofs.base.id.UniqueID;
import com.aerofs.restless.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path(Service.VERSION)
@Consumes(MediaType.APPLICATION_OCTET_STREAM)
@Produces(MediaType.APPLICATION_JSON)
public class LogsResource
{
    private static Logger l = LoggerFactory.getLogger(LogsResource.class);

    @POST
    @Path("/{org_id}/{dryad_id}/appliance/logs")
    public Response postApplianceData(
            @PathParam("org_id") long orgID,
            @PathParam("dryad_id") UniqueID dryadID,
            InputStream body)
            throws Exception
    {
        l.info("POST /{}/{}/appliance/data",
                orgID,
                dryadID.toStringFormal());

        // TODO

        return Response.ok().build();
    }

    @POST
    @Path("/{org_id}/{dryad_id}/client/{user_id}/{device_id}/logs")
    public Response postClientData(
            @PathParam("org_id") long orgID,
            @PathParam("dryad_id") UniqueID dryadID,
            @PathParam("user_id") String userID,
            @PathParam("device_id") UniqueID deviceID,
            InputStream body)
            throws Exception
    {
        l.info("POST /{}/{}/client/{}/{}", orgID, dryadID.toStringFormal(), userID,
                deviceID.toStringFormal());

        // TODO

        return Response.ok().build();
    }

    private void feedInputToOutput(InputStream input, OutputStream output)
            throws IOException
    {
        byte[] buffer = new byte[8 * 1024];

        try {
            try {
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }
}
