package com.aerofs.baseline.simple;

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
        APP.setPort((short)9999);
        APP.setDirectMemoryBacked(false);
        APP.setMaxAcceptQueueSize(10);
        APP.setNumNetworkThreads(2);
    }

    public static final HttpConfiguration ADMIN = new HttpConfiguration();

    static {
        ADMIN.setHost("localhost");
        ADMIN.setPort((short)8888);
        ADMIN.setDirectMemoryBacked(false);
        ADMIN.setMaxAcceptQueueSize(10);
        ADMIN.setNumNetworkThreads(2);
    }

    public static final DatabaseConfiguration DATABASE = new DatabaseConfiguration();

    static {
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

    public static final SimpleConfiguration SIMPLE = new SimpleConfiguration();

    static {
        SIMPLE.setMaxSeats(10);
        SIMPLE.setApp(APP);
        SIMPLE.setAdmin(ADMIN);
        SIMPLE.setDatabase(DATABASE);
        SIMPLE.setLogging(LOGGING);
    }

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
    }

    public static final String CUSTOMERS_URL = String.format("http://%s:%s/customers/", APP.getHost(), APP.getPort());

    public static final String DUMP_URL = String.format("http://%s:%s/tasks/dump/", ADMIN.getHost(), ADMIN.getPort());

    private ServerConfiguration() {
        // to prevent instantiation by subclasses
    }
}
