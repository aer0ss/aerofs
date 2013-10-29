/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.module;

import com.google.inject.Inject;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import com.aerofs.bifrost.oaaas.model.Client;

/**
 * DAO to query and update Client instances.
 */
public class ClientDAO extends AbstractDAO<Client>
{
    @Inject
    public ClientDAO(SessionFactory sessionFactory) { super(sessionFactory); }

    public Client findByClientId(String clientId)
    {
        return uniqueResult(criteria()
                .add(Restrictions.eq("clientId", clientId)));
    }

    @SuppressWarnings("unchecked")
    public <S extends Client> S save(S s) { return (S)persist(s); }
}
