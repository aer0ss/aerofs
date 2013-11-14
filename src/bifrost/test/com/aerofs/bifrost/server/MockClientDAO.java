/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.bifrost.module.ClientDAO;
import com.aerofs.bifrost.oaaas.model.Client;
import org.hibernate.SessionFactory;

import java.util.HashMap;

/**
 * Mock-database implementation for Client
 */
public class MockClientDAO extends ClientDAO
{
    private final HashMap<String, Client> m_map;

    public MockClientDAO(SessionFactory sessionFactory)
    {
        super(sessionFactory);
        m_map = new HashMap<String, Client>();
    }

    @Override
    public Client findByClientId(String clientId)
    {
        return m_map.get(clientId);
    }

    @Override
    public Client save(Client s)
    {
        return m_map.put(s.getClientId(), s);
    }
}
