/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.resources;

import com.aerofs.base.id.UniqueID;
import com.aerofs.dryad.persistence.IDryadPersistence;
import com.aerofs.restless.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;

@Path(Service.VERSION + "/appliance")
@Consumes(MediaType.APPLICATION_OCTET_STREAM)
public class ApplianceLogsResource
{
    private static Logger l = LoggerFactory.getLogger(ApplianceLogsResource.class);

    private final IDryadPersistence _persistence;

    @Inject
    public ApplianceLogsResource(IDryadPersistence persistence)
    {
        _persistence = persistence;
    }

    @POST
    @Path("/{customer_id}/{dryad_id}/logs")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response postApplianceData(
            @PathParam("customer_id") long customerID,
            @PathParam("dryad_id") UniqueID dryadID,
            InputStream body)
            throws Exception
    {
        l.info("POST /appliance/{}/{}/logs", customerID, dryadID.toStringFormal());

        try {
            _persistence.putApplianceLogs(customerID, dryadID, body);
        } finally {
            body.close();
        }

        return Response.noContent().build();
    }
}
