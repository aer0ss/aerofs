/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.base.C;
import com.aerofs.lib.LibParam;
import com.googlecode.flyway.core.Flyway;
import org.apache.tomcat.dbcp.dbcp.DriverManagerConnectionFactory;
import org.apache.tomcat.dbcp.dbcp.PoolableConnectionFactory;
import org.apache.tomcat.dbcp.dbcp.PoolingDataSource;
import org.apache.tomcat.dbcp.pool.impl.GenericObjectPool;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;
import org.hibernate.service.jdbc.connections.internal.DatasourceConnectionProviderImpl;
import org.hibernate.service.jdbc.connections.spi.ConnectionProvider;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.Properties;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getLongProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * Hibernate setup
 */
public class BifrostSessionFactory
{
    static SessionFactory build(Class<?>... entities)
    {
        final Configuration cfg = new Configuration();
        cfg.setProperty(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "managed");
        cfg.setProperty(AvailableSettings.USE_SQL_COMMENTS, "false");
        cfg.setProperty(AvailableSettings.USE_GET_GENERATED_KEYS, "true");
        cfg.setProperty(AvailableSettings.GENERATE_STATISTICS, "true");
        cfg.setProperty(AvailableSettings.USE_REFLECTION_OPTIMIZER, "true");
        cfg.setProperty(AvailableSettings.ORDER_UPDATES, "true");
        cfg.setProperty(AvailableSettings.ORDER_INSERTS, "true");
        cfg.setProperty(AvailableSettings.USE_NEW_ID_GENERATOR_MAPPINGS, "true");

        for (Class<?> klass : entities) cfg.addAnnotatedClass(klass);

        final DatasourceConnectionProviderImpl connectionProvider =
                new DatasourceConnectionProviderImpl();
        connectionProvider.setDataSource(migratedDataSource());
        connectionProvider.configure(Collections.emptyMap());

        final ServiceRegistry registry = new ServiceRegistryBuilder()
                .addService(ConnectionProvider.class, connectionProvider)
                .applySettings(Collections.emptyMap())
                .buildServiceRegistry();

        return cfg.buildSessionFactory(registry);
    }

    static DataSource migratedDataSource()
    {
        DataSource ds = dataSource();
        migrate(ds);
        return ds;
    }

    private static DataSource dataSource()
    {
        final GenericObjectPool pool = new GenericObjectPool();
        pool.setMinIdle(
                getIntegerProperty("bifrost.db.minSize", 1));
        pool.setMaxIdle(
                getIntegerProperty("bifrost.db.maxSize", 8));
        pool.setMaxActive(
                getIntegerProperty("bifrost.db.maxSize", 8));
        pool.setMaxWait(
                getLongProperty("bifrost.db.maxWaitForConnection", 1 * C.SEC));
        pool.setTestWhileIdle(
                getBooleanProperty("bifrost.db.checkConnectionWhileIdle", true));
        pool.setTimeBetweenEvictionRunsMillis(
                getLongProperty("bifrost.db.checkConnectionHealthWhenIdleFor", 10 * C.SEC));
        pool.setMinEvictableIdleTimeMillis(
                getLongProperty("bifrost.db.closeConnectionIfIdleFor", 60 * C.SEC));
        pool.setWhenExhaustedAction(GenericObjectPool.WHEN_EXHAUSTED_BLOCK);

        final Properties properties = new Properties();
        properties.setProperty("user", LibParam.MYSQL.MYSQL_USER);
        properties.setProperty("password", LibParam.MYSQL.MYSQL_PASS);

        final DriverManagerConnectionFactory factory = new DriverManagerConnectionFactory(
                "jdbc:mysql://" + LibParam.MYSQL.MYSQL_ADDRESS + "/bifrost",
                properties);

        final PoolableConnectionFactory connectionFactory = new PoolableConnectionFactory(factory,
                pool,
                null,
                getStringProperty("bifrost.db.validationQuery", ""),
                Collections.emptyList(),
                getBooleanProperty("bifrost.db.defaultReadOnly", false),
                true);
        connectionFactory.setPool(pool);

        return new PoolingDataSource(pool);
    }

    private static void migrate(DataSource ds)
    {
        // Perform database migration (with implicit initialization)
        Flyway flyway = new Flyway();
        flyway.setDataSource(ds);
        flyway.setInitOnMigrate(true);
        flyway.setSchemas("bifrost");
        flyway.migrate();
    }
}
