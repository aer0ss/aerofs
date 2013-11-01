/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.bifrost.module.ResourceServerDAO;
import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Mock-database implementation for ResourceServer
 */
public class MockResourceServerDAO extends ResourceServerDAO
{
    @Inject
    public MockResourceServerDAO(SessionFactory sessionFactory) { super(sessionFactory); }

    @Override
    public ResourceServer getByServerKey(String resourceServerKey)
    {
        return m_byServerKey.get(resourceServerKey);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S extends ResourceServer> S save(ResourceServer srv)
    {
        m_byServerKey.put(srv.getKey(), srv);
        return (S)srv;
    }

    private Map<String, ResourceServer> m_byServerKey = new HashMap<String, ResourceServer>();
}
