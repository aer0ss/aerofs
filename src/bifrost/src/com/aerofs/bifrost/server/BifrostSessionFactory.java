/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.servlets.lib.db.sql.IDataSourceProvider;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;
import org.hibernate.service.jdbc.connections.internal.DatasourceConnectionProviderImpl;
import org.hibernate.service.jdbc.connections.spi.ConnectionProvider;

import java.util.Collections;

/**
 * Hibernate setup
 */
public class BifrostSessionFactory
{
    private final IDataSourceProvider _dsProvider;

    BifrostSessionFactory(IDataSourceProvider dsProvider)
    {
        _dsProvider = dsProvider;
    }

    SessionFactory build(Class<?>... entities)
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
        connectionProvider.setDataSource(_dsProvider.getDataSource());
        connectionProvider.configure(Collections.emptyMap());

        final ServiceRegistry registry = new ServiceRegistryBuilder()
                .addService(ConnectionProvider.class, connectionProvider)
                .applySettings(Collections.emptyMap())
                .buildServiceRegistry();

        return cfg.buildSessionFactory(registry);
    }
}
