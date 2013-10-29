/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.module;

import com.google.inject.Inject;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import com.aerofs.bifrost.oaaas.model.AuthorizationRequest;

/**
 * DAO to query and update AuthorizationRequest instances.
 */
public class AuthorizationRequestDAO extends AbstractDAO<AuthorizationRequest>
{
    @Inject
    public AuthorizationRequestDAO(SessionFactory sessionFactory) { super(sessionFactory); }

    @SuppressWarnings("unchecked")
    public <S extends AuthorizationRequest> S save(S s)
    {
        s.encodePrincipal();
        s.updateTimeStamps();
        return (S)persist(s);
    }

    public AuthorizationRequest findByAuthState(String authState)
    {
        AuthorizationRequest result = uniqueResult(
                criteria().add(Restrictions.eq("authState", authState)));
        if (result != null) { result.decodePrincipal(); }
        return result;
    }

    public AuthorizationRequest findByAuthCode(String authCode)
    {
        AuthorizationRequest result = uniqueResult(
                criteria().add(Restrictions.eq("authorizationCode", authCode)));
        if (result != null) { result.decodePrincipal(); }
        return result;
    }

    public void delete(AuthorizationRequest authorizationRequest)
    {
        currentSession().delete(authorizationRequest);
    }
}
