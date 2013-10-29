/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.module;

import com.google.inject.Inject;
import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.aerofs.bifrost.oaaas.repository.ResourceServerRepository;

/**
 */
public class ResourceServerRepositoryImpl implements ResourceServerRepository
{
    @Override
    public ResourceServer findByKey(String key) { return _dao.getByServerKey(key); }

    @Override
    public <S extends ResourceServer> S save(S s) { return _dao.save(s); }

    @Inject
    private ResourceServerDAO _dao;
}