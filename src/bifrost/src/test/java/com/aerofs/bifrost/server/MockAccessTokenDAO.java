/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.bifrost.module.AccessTokenDAO;
import com.aerofs.bifrost.oaaas.model.AccessToken;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

/**
 * Mock-database implementation for AccessToken
 */
public class MockAccessTokenDAO extends AccessTokenDAO
{
    private final HashMap<String, AccessToken> m_map;

    @Inject
    public MockAccessTokenDAO(SessionFactory sessionFactory)
    {
        super(sessionFactory);
        m_map = new HashMap<>();
    }

    @Override
    public AccessToken findByToken(String tokenId) { return m_map.get(tokenId); }

    @Override
    public List<AccessToken> findByOwner(String owner)
    {
        List<AccessToken> list = new ArrayList<>(m_map.size());
        for (Entry<String, AccessToken> e : m_map.entrySet()) {
            if (e.getValue().getOwner().equals(owner)) list.add(e.getValue());
        }
        return list;
    }

    @Override
    public AccessToken save(AccessToken s)
    {
        m_map.put(s.getToken(), s);
        return s;
    }

    @Override
    public void delete(AccessToken s) { m_map.remove(s.getToken()); }

    @Override
    public void deleteDelegatedTokensByOwner(String owner)
    {
        Iterator<Entry<String,AccessToken>> iterator = m_map.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, AccessToken> e = iterator.next();
            String ow = e.getValue().getOwner();
            String ef = e.getValue().getEffectiveUserID();

            if (ow.equals(owner) && (!ow.equals(ef))) {
                iterator.remove();
            }
        }
    }

    @Override
    public void deleteAllTokensByOwner(String owner)
    {
        Iterator<Entry<String,AccessToken>> iterator = m_map.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, AccessToken> e = iterator.next();
            String ow = e.getValue().getOwner();
            if (ow.equals(owner)) {
                iterator.remove();
            }
        }
    }
}

