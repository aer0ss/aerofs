/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.resource;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.api.core.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@Path("/")
public class EventResource
{
    private static Logger l = LoggerFactory.getLogger(EventResource.class);

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
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/audit")
    public Response audit(@Context HttpContext context, Map<String, Object> contents)
    {
        // let's make sure the required elements were provided...
        if ((!contents.containsKey("event"))
                || (!contents.containsKey("timestamp"))
                || (!contents.containsKey("topic"))) {
            return Response.status(400)
                    .entity(new EventResult("Request missing required parameters"))
                    .build();
        }

        String parsed = _gson.toJson(contents);
        l.info("R: {}", parsed);
        return Response.ok().build();
    }

    /**
     *  Result type from the POST endpoint, structured for convenient Gson'ing
     */
    private static class EventResult
    {
        EventResult(String desc) { result = "error"; description = desc; }
        String result;
        String description;
    }

    private static final Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
}
