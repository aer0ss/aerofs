package com.aerofs.lib.configuration;

import java.io.File;

public class ConfigurationUtils {
    // The URL that we must GET to obtain configuration properties.
    protected static final String CONFIGURATION_URL = "http://localhost:5434";

    // Flag created by puppet that tells us we are in private deployment mode.
    private static final String PRIVATE_DEPLOYMENT_FLAG_FILE = "/etc/aerofs/private-deployment-flag";

    public static boolean isPrivateDeployment() {
        File flagFile = new File(PRIVATE_DEPLOYMENT_FLAG_FILE);
        return flagFile.exists();
    }

    public static class ExHttpConfig extends Exception
    {
        private static final long serialVersionUID = 1L;

        public ExHttpConfig(String msg)
        {
            super(msg);
        }
    }
}
