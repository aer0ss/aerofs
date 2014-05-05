/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.resources;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.dryad.persistence.IDryadPersistence;
import com.aerofs.restless.Service;
import com.aerofs.restless.util.ContentRange;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;

import static com.aerofs.restless.util.ContentRange.rangeNullable;

@Path(Service.VERSION + "/client")
@Consumes(MediaType.APPLICATION_OCTET_STREAM)
public class ClientLogsResource
{
    private static Logger l = LoggerFactory.getLogger(ClientLogsResource.class);

    private final IDryadPersistence _persistence;

    @Inject
    public ClientLogsResource(IDryadPersistence persistence)
    {
        _persistence = persistence;
    }

    @POST
    @Path("/{customer_id}/{dryad_id}/{user_id}/{device_id}/logs")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response postClientData(
            @PathParam("customer_id") long customerID,
            @PathParam("dryad_id") UniqueID dryadID,
            @PathParam("user_id") String userID,
            @PathParam("device_id") DID deviceID,
            @HeaderParam(Names.CONTENT_RANGE) ContentRange contentRange,
            InputStream body)
            throws Exception
    {
        l.info("POST /client/{}/{}/{}/{}/logs", customerID, dryadID.toStringFormal(), userID,
                deviceID.toStringFormal());

        try {
            // convert userID to UserID to validate user ID
            _persistence.putClientLogs(customerID, dryadID, UserID.fromExternal(userID), deviceID,
                    body, rangeNullable(contentRange));
        } finally {
            body.close();
        }

        return Response.noContent().build();
    }
}
