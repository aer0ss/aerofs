/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.resources;

import com.aerofs.dryad.Blacklist.DeviceBlacklist;
import com.aerofs.dryad.Blacklist.UserBlacklist;
import com.aerofs.dryad.LogStore;
import com.aerofs.restless.Service;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static com.aerofs.dryad.DryadProperties.DIR_ARCHIVED;
import static com.aerofs.dryad.DryadProperties.DIR_DEFECTS;

@Path(Service.VERSION + "/")
public class LogsResource
{
    private static final Logger l = LoggerFactory.getLogger(LogsResource.class);
    private static final Pattern ID_FORMAT = Pattern.compile("^[0-9a-fA-F]{32}$");

    private final LogStore _logStore;
    private final UserBlacklist _userBlacklist;
    private final DeviceBlacklist _deviceBlacklist;

    @Context private UriInfo _uriInfo;

    @Inject
    public LogsResource(LogStore logStore, UserBlacklist userBlacklist, DeviceBlacklist deviceBlacklist)
    {
        _logStore = logStore;
        _userBlacklist = userBlacklist;
        _deviceBlacklist = deviceBlacklist;
    }

    @PUT
    @Path("/defects/{defect_id}/appliance")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response storeApplianceDefectsLogs(
            @PathParam("defect_id") String defectID,
            InputStream body)
            throws Exception
    {
        l.info("PUT {}", _uriInfo.getPath());

        throwIfInvalidDefectID(defectID);

        String filePath = format("%s/%s/appliance.zip", DIR_DEFECTS, defectID);
        l.debug("Saving defect {}/appliance to {}", defectID, filePath);
        storeLogs(body, filePath);
        return Response.noContent().build();
    }

    @PUT
    @Path("/defects/{defect_id}/client/{user_id}/{device_id}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response storeClientDefectsLogs(
            @PathParam("defect_id") String defectID,
            @PathParam("user_id") String userID,
            @PathParam("device_id") String deviceID,
            InputStream body)
            throws Exception
    {
        l.info("PUT {}", _uriInfo.getPath());

        _userBlacklist.throwIfBlacklisted(userID);
        _deviceBlacklist.throwIfBlacklisted(deviceID);
        throwIfInvalidDefectID(defectID);
        throwIfInvalid(userID, deviceID);

        String filePath = format("%s/%s/%s_%s.zip", DIR_DEFECTS, defectID, userID, deviceID);
        l.debug("Saving defect {}/client to {}", defectID, filePath);
        storeLogs(body, filePath);
        return Response.noContent().build();
    }

    @PUT
    @Path("/archived/{user_id}/{device_id}/{filename}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response storeArchivedLogs(
            @PathParam("user_id") String userID,
            @PathParam("device_id") String deviceID,
            @PathParam("filename") String filename,
            InputStream body)
            throws Exception
    {
        l.info("PUT {}", _uriInfo.getPath());

        _userBlacklist.throwIfBlacklisted(userID);
        _deviceBlacklist.throwIfBlacklisted(deviceID);
        throwIfInvalid(userID, deviceID, filename);

        String filePath = format("%s/%s/%s_%s", DIR_ARCHIVED, userID, deviceID, filename);
        l.debug("Saving archived logs from {}/{} to {}", userID, deviceID, filePath);
        storeLogs(body, filePath);
        return Response.noContent().build();
    }

    private void storeLogs(InputStream src, String dest)
            throws IOException
    {
        try {
            _logStore.storeLogs(src, dest);
        } finally {
            src.close();
        }
    }

    // TODO: there's gotta be a better way of checking this
    private void throwIfInvalid(String... args)
            throws IllegalArgumentException
    {
        for (String arg : args) {
            checkArgument(!arg.equals("..") && !arg.equals(".") && !arg.contains("/"),
                    format("Invalid argument: %s", arg));
        }
    }

    private void throwIfInvalidDefectID(String defectID)
            throws IllegalArgumentException
    {
        // new defect ID format.
        if (ID_FORMAT.matcher(defectID).matches()) return;

        // old defect ID format, this throws IllegalArgumentException if invalid
        UUID.fromString(defectID);
    }
}
