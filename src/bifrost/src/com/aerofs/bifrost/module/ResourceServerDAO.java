/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.module;

import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;

/**
 * DAO to query and update ResourceServer instances.
 */
public class ResourceServerDAO extends AbstractDAO<ResourceServer>
{
    @Inject
    public ResourceServerDAO(SessionFactory sessionFactory) { super(sessionFactory); }

    public ResourceServer getByServerKey(String resourceServerKey)
    {
        return uniqueResult(criteria()
                .add(Restrictions.eq("key", resourceServerKey)));
    }

    public ResourceServer save(ResourceServer srv) { return persist(srv); }
}
