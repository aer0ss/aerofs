/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.module;

import com.aerofs.bifrost.oaaas.model.AccessToken;
import com.google.inject.Inject;
import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.Delete;

import java.util.List;


/**
 * DAO to query and update AccessToken instances
 */
public class AccessTokenDAO extends AbstractDAO<AccessToken>
{
    @Inject
    public AccessTokenDAO(SessionFactory sessionFactory) { super(sessionFactory); }

    public AccessToken save(AccessToken s)
    {
        s.encodePrincipal();
        s.updateTimeStamps();
        return persist(s);
    }

    public AccessToken findByToken(String tokenId)
    {
        // FIXME: this workaround should not be needed, @PostUpdate should decode principal
        AccessToken token = uniqueResult(criteria().add(Restrictions.eq("token", tokenId)));
        if (token != null) { token.decodePrincipal(); }
        return token;
    }

    public List<AccessToken> findByOwner(String owner)
    {
        // TODO: should we index this field?
        Query q = currentSession().createQuery("from AccessToken A where A.owner = :owner");
        q.setParameter("owner", owner);
        return list(q);
    }

    public void deleteAllTokensByOwner(String owner)
    {
        Query q = currentSession().createQuery("delete from AccessToken where owner = :owner");
        q.setParameter("owner", owner);
        q.executeUpdate();
    }

    public void deleteDelegatedTokensByOwner(String owner)
    {
        Query q = currentSession().createQuery("delete from AccessToken where owner = :owner and resourceOwnerId != :owner");
        q.setParameter("owner", owner);
        q.executeUpdate();
    }
}
