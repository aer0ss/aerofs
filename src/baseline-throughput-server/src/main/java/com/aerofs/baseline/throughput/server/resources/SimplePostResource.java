package com.aerofs.baseline.throughput.server.resources;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

@Path("/post")
@Singleton
public final class SimplePostResource {

    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    public void noopWrite(String content) {
        // noop
    }
}
