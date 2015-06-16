package com.aerofs.lib.configuration;

public class ConfigurationUtils {
    // The URL that we must GET to obtain configuration properties.
    protected static final String CONFIGURATION_URL = "http://config.service:5434";

    public static class ExHttpConfig extends Exception
    {
        private static final long serialVersionUID = 1L;

        public ExHttpConfig(String msg)
        {
            super(msg);
        }
    }
}
