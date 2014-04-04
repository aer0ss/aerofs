/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.resources;

import java.io.InputStream;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.dryad.storage.ClientLogsDryadDatabase;
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

@Path(Service.VERSION + "/client")
@Consumes(MediaType.APPLICATION_OCTET_STREAM)
public class ClientLogsResource
{
    private static Logger l = LoggerFactory.getLogger(ApplianceLogsResource.class);

    private final ClientLogsDryadDatabase _clientDatabase;

    public ClientLogsResource(ClientLogsDryadDatabase clientDatabase)
    {
        _clientDatabase = clientDatabase;
    }

    @POST
    @Path("/{org_id}/{dryad_id}/{user_id}/{device_id}/logs")
    public Response postClientData(
            @PathParam("org_id") long orgID,
            @PathParam("dryad_id") UniqueID dryadID,
            @PathParam("user_id") String userID,
            @PathParam("device_id") DID deviceID,
            InputStream body)
            throws Exception
    {
        l.info("POST /client/{}/{}/{}/{}/logs", orgID, dryadID.toStringFormal(), userID,
                deviceID.toStringFormal());

        if (_clientDatabase.logsExist(orgID, dryadID, UserID.fromExternal(userID), deviceID)) {
            return Response.status(Status.FORBIDDEN).build();
        }

        _clientDatabase.putLogs(orgID, dryadID, UserID.fromExternal(userID), deviceID, body);

        return Response.noContent().build();
    }
}