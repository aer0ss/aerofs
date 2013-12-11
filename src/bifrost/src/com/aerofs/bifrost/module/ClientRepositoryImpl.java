/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.module;

import com.google.inject.Inject;
import com.aerofs.bifrost.oaaas.model.Client;
import com.aerofs.bifrost.oaaas.repository.ClientRepository;
import org.hibernate.Query;

import java.util.List;

public class ClientRepositoryImpl implements ClientRepository
{
    @Override
    public Client findByClientId(String clientId) { return _dao.findByClientId(clientId); }

    @Override
    public <S extends Client> S save(S s) { _dao.save(s); return s; }

    @Override
    public <S extends Client> void delete(S s) { _dao.delete(s); }

    @Override
    public List<Client> listAll()
    {
        Query query = _dao.currentSession().createQuery("from Client");
        return _dao.list(query);
    }

    @Inject private ClientDAO _dao;
}
