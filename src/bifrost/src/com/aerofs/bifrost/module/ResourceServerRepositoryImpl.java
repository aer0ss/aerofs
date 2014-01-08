/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.module;

import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.aerofs.bifrost.oaaas.repository.ResourceServerRepository;
import com.google.inject.Inject;

/**
 */
public class ResourceServerRepositoryImpl implements ResourceServerRepository
{
    @Override
    public ResourceServer findByKey(String key) { return _dao.getByServerKey(key); }

    @Override
    public ResourceServer save(ResourceServer s) { return _dao.save(s); }

    @Inject
    private ResourceServerDAO _dao;
}