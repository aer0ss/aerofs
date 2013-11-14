/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.server;

import com.aerofs.bifrost.module.AuthorizationRequestDAO;
import com.aerofs.bifrost.oaaas.model.AuthorizationRequest;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Mock-database implementation for AuthorizationRequest
 */
public class MockAuthRequestDAO extends AuthorizationRequestDAO
{
    private Map<String, AuthorizationRequest> m_state = new HashMap<String, AuthorizationRequest>();
    private Map<String, AuthorizationRequest> m_code = new HashMap<String, AuthorizationRequest>();

    @Inject
    public MockAuthRequestDAO(SessionFactory sessionFactory)
    {
        super(sessionFactory);
    }

    @Override
    public AuthorizationRequest save(AuthorizationRequest s)
    {
        m_state.put(s.getAuthState(), s);
        m_code.put(s.getAuthorizationCode(), s);
        return s;
    }

    @Override
    public AuthorizationRequest findByAuthState(String authState) { return m_state.get(authState); }

    @Override
    public AuthorizationRequest findByAuthCode(String authCode) { return m_code.get(authCode); }

    @Override
    public void delete(AuthorizationRequest authorizationRequest)
    {
        m_state.remove(authorizationRequest.getAuthState());
        m_code.remove(authorizationRequest.getAuthorizationCode());
    }
}
