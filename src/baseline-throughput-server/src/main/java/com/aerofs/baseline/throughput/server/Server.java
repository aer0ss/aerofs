package com.aerofs.baseline.throughput.server;

import com.aerofs.baseline.Environment;
import com.aerofs.baseline.Service;

public final class Server extends Service<ServerConfiguration> {

    public static void main(String[] args) throws Exception {
        Server server = new Server();
        server.run(args);
    }

    @Override
    public void init(ServerConfiguration configuration, Environment environment) throws Exception {
        addResource(SimpleGetResource.class);
        addResource(ChunkedUploadResource.class);
        addResource(JsonParsingResource.class);
        addResource(LongPollingResource.class);
    }
}