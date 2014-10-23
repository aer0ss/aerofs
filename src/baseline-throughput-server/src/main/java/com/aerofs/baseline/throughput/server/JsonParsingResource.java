package com.aerofs.baseline.throughput.server;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

@Path("/json")
@Singleton
public final class JsonParsingResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void consumeJson(JsonObject object) {
        // noop
    }
}
