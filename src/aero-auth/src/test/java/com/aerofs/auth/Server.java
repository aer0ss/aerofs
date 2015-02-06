package com.aerofs.auth;

import com.aerofs.baseline.AdminEnvironment;
import com.aerofs.baseline.RootEnvironment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceEnvironment;
import com.aerofs.baseline.config.Configuration;

/**
 * Server used to test different AeroFS authentication mechanisms.
 * <br>
 * Intentionally left non-final so that subclasses can setup
 * test-specific authenticators, resources, providers, etc.
 */
public class Server extends Service<Server.ServerConfiguration> {

    /**
     * Constructor.
     */
    public Server() {
        super("test-server");
    }

    @Override
    public void init(ServerConfiguration configuration, RootEnvironment root, AdminEnvironment admin, ServiceEnvironment service) throws Exception {
        // nothing to configure by default...
    }

    /**
     * Configuration for our test server.
     */
    public static final class ServerConfiguration extends Configuration {

        // does not define any fields beyond those in baseline
    }
}
