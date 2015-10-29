package com.aerofs.trifrost.resources;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.trifrost.api.Notification;
import com.aerofs.trifrost.base.DeviceNotFoundException;
import com.aerofs.trifrost.base.UserNotAuthorizedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.HashMap;

/**
 */
@Path("/notify")
@PermitAll
@Api(value = "offline notifications",
        produces = "application/json",
        consumes = "application/json")
public class NotificationResource {
    private static final Logger logger = LoggerFactory.getLogger(AuthResource.class);

    private final boolean pushEnabled;
    private final String sendNotifUrl;
    private final String authHeader;
    private final String authValue;

    public NotificationResource() throws IOException {
        this.pushEnabled = ConfigurationProperties.getBooleanProperty("messaging.push.enabled", false);
        this.sendNotifUrl = ConfigurationProperties.getStringProperty("messaging.push.url.send", "misconfigured");
        this.authHeader = ConfigurationProperties.getStringProperty("messaging.push.auth.header", "Authorization");
        this.authValue = ConfigurationProperties.getStringProperty("messaging.push.auth.value", "misconfigured");
    }

    // FIXME: This is experimental : unify all use of push through trifrost? Why not?
    // Do not expose this endpoint via nginx; it is for internal services only
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Send a notification to a set of devices")
    public void sendNotification(@NotNull Notification notif) throws DeviceNotFoundException, UserNotAuthorizedException, JsonProcessingException {
        if (!pushEnabled) {
            logger.info("Skipping push notify: push is disabled");
        }

        HashMap<String, Object> envelope = new HashMap<>();
        HashMap<String, String> messageBody = new HashMap<>();
        messageBody.put("alert", notif.alert);

        envelope.put("message", messageBody);
        envelope.put("alias", new String[] { notif.target } );

        ObjectMapper mapper = new ObjectMapper();
        String requestJson = mapper.writeValueAsString(envelope);

        try {
            Request.Post(this.sendNotifUrl)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader(
                            this.authHeader,
                            this.authValue)
                    .bodyString(requestJson, ContentType.APPLICATION_JSON)
                    .execute()
                    .handleResponse(response -> {
                        logger.debug("Sent notification to devices: {}", response.toString());
                        return null;
                    });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
