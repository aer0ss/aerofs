package com.aerofs.trifrost.resources;

import com.aerofs.servlets.lib.AbstractEmailSender;
import com.aerofs.trifrost.UnifiedPushConfiguration;
import com.aerofs.trifrost.base.Constants;
import com.aerofs.trifrost.api.Device;
import com.aerofs.trifrost.base.DeviceNotFoundException;
import com.aerofs.trifrost.base.UniqueIDGenerator;
import com.aerofs.trifrost.base.UserNotAuthorizedException;
import com.aerofs.trifrost.db.Devices;
import com.aerofs.trifrost.model.AuthorizedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;

/**
 */
@Path("/devices")
@PermitAll
public class DeviceResource {
    private final DBI dbi;
    private final UnifiedPushConfiguration unifiedPushConfiguration;
    private static final Logger logger = LoggerFactory.getLogger(AuthResource.class);

    public DeviceResource(@Context DBI dbi,
                        @Context AbstractEmailSender mailSender,
                        @Context UnifiedPushConfiguration unifiedPushConfiguration,
                        @Context UniqueIDGenerator uniqueID) throws IOException {
        this.dbi = dbi;
        this.unifiedPushConfiguration = unifiedPushConfiguration;
    }

    @PUT
    @RolesAllowed(Constants.USER_ROLE)
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{deviceid}")
    public Response updateDevice(
            @PathParam("deviceid") final String deviceId,
            @NotNull Device update,
            @Context AuthorizedUser authorizedUser)
            throws DeviceNotFoundException, UserNotAuthorizedException {
        Preconditions.checkNotNull(authorizedUser);
        logger.info("u:{} PUT u:{} d:{}", authorizedUser.id, deviceId);

        return dbi.inTransaction((conn, status) -> {
            Devices deviceTable = conn.attach(Devices.class);
            Device deviceRow = deviceTable.get(authorizedUser.id, deviceId);
            if (deviceRow == null) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }

            Device merged = mergeDevices(deviceRow, update);
            deviceTable.update(authorizedUser.id, deviceId, merged);

            saveDeviceToUnifiedPushServer(merged);

            logger.warn("update device for user {} device {}", authorizedUser.id, deviceId);
            return Response.status(Response.Status.CREATED).build();
        });
    }


    private Device mergeDevices(Device old, Device update) {
        Preconditions.checkNotNull(old);
        Preconditions.checkNotNull(update);
        if (!Strings.isNullOrEmpty(update.getName())) {
            old.setName(update.getName());
        }
        if (!Strings.isNullOrEmpty(update.getFamily())) {
            old.setFamily(update.getFamily());
        }
        if (update.getPushType() != null) {
            old.setPushType(update.getPushType());
            old.setPushToken(update.getPushToken());
        }
        return old;
    }


    public void saveDeviceToUnifiedPushServer(Device device) {
        try {
            String serverUrl = unifiedPushConfiguration.getServerURL();
            String registerDeviceUrl = serverUrl + "rest/registry/device";
            String basicAuthToken = "Basic " + unifiedPushConfiguration.getBasicAuthToken(device.getPushType());

            HashMap<String, String> deviceMap = new HashMap<>();
            deviceMap.put("deviceToken", device.getPushToken());
            deviceMap.put("alias", device.getPushToken()); // register the device token as an alias to allow single-device pushes
            ObjectMapper mapper = new ObjectMapper();
            String deviceJson = mapper.writeValueAsString(deviceMap);

            Request.Post(registerDeviceUrl)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", basicAuthToken)
                    .bodyString(deviceJson, ContentType.APPLICATION_JSON)
                    .execute()
                    .handleResponse(response -> {
                        logger.debug("Registered device with push server: {}", response.toString());
                        return null;
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
