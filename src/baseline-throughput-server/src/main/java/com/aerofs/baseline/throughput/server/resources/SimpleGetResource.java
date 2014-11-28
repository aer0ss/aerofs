package com.aerofs.baseline.throughput.server.resources;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Random;

@Path("/get")
@Singleton
public final class SimpleGetResource {

    private final Random random = new Random();

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public long getNumber() {
        return random.nextLong();
    }
}
