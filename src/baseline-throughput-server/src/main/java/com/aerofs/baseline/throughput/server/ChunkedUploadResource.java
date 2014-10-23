package com.aerofs.baseline.throughput.server;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

@Path("/chunks")
@Singleton
public final class ChunkedUploadResource {

    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void upload(byte[] bytes) {
        // noop
    }
}
