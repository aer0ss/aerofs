/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.module;

import com.google.inject.Inject;
import com.aerofs.bifrost.oaaas.model.Client;
import com.aerofs.bifrost.oaaas.repository.ClientRepository;

public class ClientRepositoryImpl implements ClientRepository
{
    @Override
    public Client findByClientId(String clientId) { return _dao.findByClientId(clientId); }

    @Override
    public <S extends Client> S save(S s) { _dao.save(s); return s; }

    @Inject private ClientDAO _dao;
}
