package com.aerofs.baseline.throughput.server;

import com.aerofs.baseline.AdminEnvironment;
import com.aerofs.baseline.RootEnvironment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceEnvironment;
import com.aerofs.baseline.throughput.server.resources.ChunkedUploadResource;
import com.aerofs.baseline.throughput.server.resources.JsonParsingResource;
import com.aerofs.baseline.throughput.server.resources.LongPollingResource;
import com.aerofs.baseline.throughput.server.resources.SimpleGetResource;
import com.aerofs.baseline.throughput.server.resources.SimplePostResource;

public final class Server extends Service<ServerConfiguration> {

    public static void main(String[] args) throws Exception {
        Server server = new Server();
        server.run(args);
    }

    public Server() {
        super("baseline-throughput-server");
    }

    @Override
    public void init(ServerConfiguration configuration, RootEnvironment root, AdminEnvironment admin, ServiceEnvironment service) throws Exception {
        service.addResource(SimpleGetResource.class);
        service.addResource(SimplePostResource.class);
        service.addResource(ChunkedUploadResource.class);
        service.addResource(JsonParsingResource.class);
        service.addResource(LongPollingResource.class);
    }
}