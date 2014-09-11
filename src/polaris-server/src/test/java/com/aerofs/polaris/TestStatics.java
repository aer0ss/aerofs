package com.aerofs.polaris;

import ch.qos.logback.classic.Level;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.http.HttpConfiguration;
import com.aerofs.baseline.logging.LoggingConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

abstract class TestStatics {

    public static final HttpConfiguration APP_CONFIGURATION = new HttpConfiguration();
    static {
        APP_CONFIGURATION.setHost("localhost");
        APP_CONFIGURATION.setPort((short) 9999);
        APP_CONFIGURATION.setMaxAcceptQueueSize(10);
        APP_CONFIGURATION.setNumNetworkThreads(2);
    }

    public static final HttpConfiguration ADMIN_CONFIGURATION = new HttpConfiguration();
    static {
        ADMIN_CONFIGURATION.setHost("localhost");
        ADMIN_CONFIGURATION.setPort((short) 8888);
        ADMIN_CONFIGURATION.setMaxAcceptQueueSize(10);
        ADMIN_CONFIGURATION.setNumNetworkThreads(2);
    }

    public static final DatabaseConfiguration DATABASE_CONFIGURATION = new DatabaseConfiguration();
    static {
        DATABASE_CONFIGURATION.setDriverClass("org.h2.Driver");
        DATABASE_CONFIGURATION.setUrl("jdbc:h2:mem:test");
        DATABASE_CONFIGURATION.setUsername("test");
        DATABASE_CONFIGURATION.setPassword("test");
    }

    public static final LoggingConfiguration LOGGING_CONFIGURATION = new LoggingConfiguration();
    static {
        LOGGING_CONFIGURATION.setLevel(Level.ALL.levelStr);
    }

    public static final PolarisConfiguration POLARIS_CONFIGURATION = new PolarisConfiguration();
    static {
        POLARIS_CONFIGURATION.setApp(APP_CONFIGURATION);
        POLARIS_CONFIGURATION.setAdmin(ADMIN_CONFIGURATION);
        POLARIS_CONFIGURATION.setDatabase(DATABASE_CONFIGURATION);
        POLARIS_CONFIGURATION.setLogging(LOGGING_CONFIGURATION);
    }

    public static final String POLARIS_URI = String.format("http://%s:%s", APP_CONFIGURATION.getHost(), APP_CONFIGURATION.getPort());
    public static final String POLARIS_ADMIN_URI = String.format("http://%s:%s", ADMIN_CONFIGURATION.getHost(), ADMIN_CONFIGURATION.getPort());

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static {
        OBJECT_MAPPER.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
    }

    private TestStatics() {
        // to prevent instantiation by subclasses
    }
}
