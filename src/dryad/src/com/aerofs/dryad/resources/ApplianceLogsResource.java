/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.resources;

import java.io.InputStream;

import com.aerofs.base.id.UniqueID;
import com.aerofs.dryad.storage.ApplianceLogsDryadDatabase;
import com.aerofs.restless.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@Path(Service.VERSION + "/appliance")
@Consumes(MediaType.APPLICATION_OCTET_STREAM)
public class ApplianceLogsResource
{
    private static Logger l = LoggerFactory.getLogger(ApplianceLogsResource.class);

    private final ApplianceLogsDryadDatabase _applianceDatabase;

    public ApplianceLogsResource(ApplianceLogsDryadDatabase applianceDatabase)
    {
        _applianceDatabase = applianceDatabase;
    }

    @POST
    @Path("/{org_id}/{dryad_id}/logs")
    public Response postApplianceData(
            @PathParam("org_id") long orgID,
            @PathParam("dryad_id") UniqueID dryadID,
            InputStream body)
            throws Exception
    {
        l.info("POST /appliance/{}/{}/logs",
                orgID,
                dryadID.toStringFormal());

        if (_applianceDatabase.logsExist(orgID, dryadID)) {
            return Response.status(Status.FORBIDDEN).build();
        }

        _applianceDatabase.putLogs(orgID, dryadID, body);

        return Response.noContent().build();
    }
}