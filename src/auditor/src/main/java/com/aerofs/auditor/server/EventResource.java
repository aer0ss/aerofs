/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.auditor.downstream.Downstream;
import com.aerofs.baseline.auth.aero.AeroPrincipal;
import com.aerofs.baseline.auth.aero.Roles;
import com.aerofs.lib.log.LogUtil;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jboss.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

/**
 * An endpoint that allows submission of auditable events.
 */
@Singleton
@Path("/")
public class EventResource
{
    static class VerifiedSubmitter
    {
        public final String userId;
        public final String deviceId;

        public VerifiedSubmitter(String userId, String deviceId)
        {
            this.userId = userId;
            this.deviceId = deviceId;
        }
    }

    private static Logger l = LoggerFactory.getLogger(EventResource.class);

    @Inject
    private Downstream.AuditChannel _auditChannel;

    /**
     * Endpoint that accepts auditable events.
     *
     * The POST body must be a JSON document. The event will be rejected if it does not contain
     * the following elements:<ul>
     *     <li>topic: one of the types supported by AuditClient</li>
     *     <li>event: short event title</li>
     *     <li>timestamp: a stamp in time</li>
     * </ul>
     *
     * Message response will be a JSON document that indicates the event-submission result. If
     * the document was not accepted, a brief error message will be returned in the JSON doc.
     */
    @POST
    @RolesAllowed({Roles.USER, Roles.SERVICE})
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/event")
    public Response event(@Nullable @Context AeroPrincipal principal, Map<String, Object> contents)
    {
        // let's make sure the required elements were provided...
        if ((contents == null)
                || (!contents.containsKey("event"))
                || (!contents.containsKey("timestamp"))
                || (!contents.containsKey("topic"))) {
            return Response.status(400)
                    .entity(new EventResult("Request missing required parameters"))
                    .build();
        }

        if (principal != null) {
            String userId = principal.getName();
            String deviceId = principal.getDevice();
            contents.put("verified_submitter", new VerifiedSubmitter(userId, deviceId));
        }

        String parsed = _gson.toJson(contents);
        l.debug("R: {}", parsed);

        try {
            ChannelFuture future = _auditChannel.doSend(parsed);

            // I'd rather use a channel listener here, but the overarching constraint is
            // the synchronous nature of Jersey. We build it this way to avoid trying to
            // shoehorn async handling in JerseyHandler and friends.
            // See the use of ExecutionHandler before this handler.
            // TODO: assert not in io thread
            if (future.syncUninterruptibly().isSuccess()) {
                return Response.ok().build();
            }
        } catch (Exception e) {
            l.warn("Downstream svc unavailable", LogUtil.suppress(e));
        }
        return Response.status(500).build();
    }

    /**
     *  Result type from the POST endpoint, structured for convenient Gson'ing
     */
    @SuppressWarnings("unused")
    private static class EventResult
    {
        EventResult(String desc) { result = "error"; description = desc; }
        String result;
        String description;
    }

    private static final Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .create();
}
