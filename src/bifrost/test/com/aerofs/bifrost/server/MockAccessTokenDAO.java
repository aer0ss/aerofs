/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.bifrost.module.AccessTokenDAO;
import com.aerofs.bifrost.oaaas.model.AccessToken;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;

import java.util.HashMap;

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
        m_map = new HashMap<String, AccessToken>();
    }

    @Override
    public AccessToken findByToken(String tokenId) { return m_map.get(tokenId); }

    @SuppressWarnings("unchecked")
    @Override
    public <S extends AccessToken> S save(S s) { return (S)m_map.put(s.getToken(), s); }
}
