/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.module;

import com.google.inject.Inject;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import com.aerofs.bifrost.oaaas.model.AccessToken;


/**
 * DAO to query and update AccessToken instances
 */
public class AccessTokenDAO extends AbstractDAO<AccessToken>
{
    @Inject
    public AccessTokenDAO(SessionFactory sessionFactory) { super(sessionFactory); }

    @SuppressWarnings("unchecked")
    public <S extends AccessToken> S save(S s)
    {
        s.encodePrincipal();
        s.updateTimeStamps();
        return (S)persist(s);
    }

    public AccessToken findByToken(String tokenId)
    {
        // FIXME: this workaround should not be needed, @PostUpdate should decode principal
        AccessToken token = uniqueResult(criteria().add(Restrictions.eq("token", tokenId)));
        if (token != null) { token.decodePrincipal(); }
        return token;
    }
}
