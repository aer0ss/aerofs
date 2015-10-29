package com.aerofs.trifrost.resources;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.servlets.lib.AbstractEmailSender;
import com.aerofs.trifrost.api.Device;
import com.aerofs.trifrost.base.Constants;
import com.aerofs.trifrost.base.DeviceNotFoundException;
import com.aerofs.trifrost.base.UniqueIDGenerator;
import com.aerofs.trifrost.base.UserNotAuthorizedException;
import com.aerofs.trifrost.db.Devices;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
@Api(value = "device parameters",
        produces = "application/json",
        consumes = "application/json")
public class DeviceResource {
    private static final Logger logger = LoggerFactory.getLogger(AuthResource.class);
    private final DBI dbi;

    private final boolean pushEnabled;
    private final String registerIosDevice;
    private final String registerAndroidDevice;
    private final String authHeader;
    private final String authValue;

    public DeviceResource(@Context DBI dbi,
                          @Context AbstractEmailSender mailSender,
                          @Context UniqueIDGenerator uniqueID) throws IOException {
        this.dbi = dbi;

        this.pushEnabled = ConfigurationProperties.getBooleanProperty("messaging.push.enabled", false);
        this.registerIosDevice = ConfigurationProperties.getStringProperty("messaging.push.url.register.ios", "misconfigured");
        this.registerAndroidDevice = ConfigurationProperties.getStringProperty("messaging.push.url.register.gcm", "misconfigured");
        this.authHeader = ConfigurationProperties.getStringProperty("messaging.push.auth.header", "Authorization");
        this.authValue = ConfigurationProperties.getStringProperty("messaging.push.auth.value", "misconfigured");
    }

    @PUT
    @RolesAllowed(Constants.USER_ROLE)
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{deviceid}")
    @ApiOperation(value = "Register for push notification",
            notes = "Update device parameters (name and family), or register for push notification services.\n\n" +
                    "see the Device model for more information; APNS and GCM registration are currently supported."
    )
    public Response updateDevice(
            @PathParam("deviceid") final String deviceId,
            @NotNull Device update,
            @Context AuthorizedUser authorizedUser)
            throws DeviceNotFoundException, UserNotAuthorizedException {
        Preconditions.checkNotNull(authorizedUser);
        logger.info("u:{} PUT d:{}", authorizedUser.id, deviceId);

        return dbi.inTransaction((conn, status) -> {
            Devices deviceTable = conn.attach(Devices.class);
            Device deviceRow = deviceTable.get(authorizedUser.id, deviceId);
            if (deviceRow == null) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }

            Device merged = mergeDevices(deviceRow, update);
            deviceTable.update(authorizedUser.id, deviceId, merged);

            saveDeviceToUnifiedPushServer(merged, authorizedUser.id);

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


    public void saveDeviceToUnifiedPushServer(Device device, String userAlias) {
        logger.info("Preparing push service registration");
        if (!this.pushEnabled) {
            logger.info("Skipping push registration (disabled)");
            return;
        }
        String requestPath = "";
        if (device.getPushType() == Device.PushType.APNS) {
            requestPath = registerIosDevice;
        } else if (device.getPushType() == Device.PushType.GCM) {
            requestPath = registerAndroidDevice;
        } else {
            logger.info("No device registration for this push type");
            return;
        }

        try {
            HashMap<String, String> deviceMap = new HashMap<>();
            deviceMap.put("deviceToken", device.getPushToken());
            deviceMap.put("alias", userAlias);
            ObjectMapper mapper = new ObjectMapper();
            String deviceJson = mapper.writeValueAsString(deviceMap);

            Request.Post(requestPath)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader(
                            this.authHeader,
                            this.authValue)
                    .bodyString(deviceJson, ContentType.APPLICATION_JSON)
                    .execute()
                    .handleResponse(response -> {
                        logger.info("Registered device with push server: {}", response.toString());
                        return null;
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
