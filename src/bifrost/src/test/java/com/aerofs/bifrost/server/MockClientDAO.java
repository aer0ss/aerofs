/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.bifrost.module.ClientDAO;
import com.aerofs.bifrost.oaaas.model.Client;
import org.hibernate.Query;
import org.hibernate.SessionFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Mock-database implementation for Client
 */
public class MockClientDAO extends ClientDAO
{
    private final HashMap<String, Client> m_map;

    public MockClientDAO(SessionFactory sessionFactory)
    {
        super(sessionFactory);
        m_map = new HashMap<>();
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

    @Override
    public List<Client> list(Query query)
    {
      // always returns all clients, regardless of criteria
      return new ArrayList<>(m_map.values());
    }

    @Override
    public void delete(Client s)
    {
        m_map.remove(s.getClientId());
    }
}
