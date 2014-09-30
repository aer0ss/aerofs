package com.aerofs.polaris;

import ch.qos.logback.classic.Level;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.http.HttpConfiguration;
import com.aerofs.baseline.logging.LoggingConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

public abstract class ServerConfiguration {

    public static final HttpConfiguration APP = new HttpConfiguration();
    static {
        APP.setHost("localhost");
        APP.setPort((short) 9999);
        APP.setMaxAcceptQueueSize(10);
        APP.setNumNetworkThreads(2);
    }

    public static final HttpConfiguration ADMIN = new HttpConfiguration();
    static {
        ADMIN.setHost("localhost");
        ADMIN.setPort((short) 8888);
        ADMIN.setMaxAcceptQueueSize(10);
        ADMIN.setNumNetworkThreads(2);
    }

    public static final DatabaseConfiguration DATABASE = new DatabaseConfiguration();
    static {
        // DATABASE.setUrl("jdbc:h2:mem:test;INIT=CREATE SCHEMA IF NOT EXISTS polaris\\;SET SCHEMA polaris"); (works, but only if flyway.setSchemas uses POLARIS as the schema name)
        DATABASE.setUrl("jdbc:h2:mem:test");
        DATABASE.setDriverClass("org.h2.Driver");
        DATABASE.setUsername("test");
        DATABASE.setPassword("test");
        DATABASE.setMinIdleConnections(1); // explicitly keep one connection around to prevent the db from being harvested
    }

    public static final LoggingConfiguration LOGGING = new LoggingConfiguration();
    static {
        LOGGING.setLevel(Level.ALL.levelStr);
    }

    public static final PolarisConfiguration POLARIS = new PolarisConfiguration();
    static {
        POLARIS.setApp(APP);
        POLARIS.setAdmin(ADMIN);
        POLARIS.setDatabase(DATABASE);
        POLARIS.setLogging(LOGGING);
    }

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static {
        OBJECT_MAPPER.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
    }

    private ServerConfiguration() {
        // to prevent instantiation by subclasses
    }
}
