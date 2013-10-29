/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.module;

import com.google.inject.Inject;
import com.aerofs.bifrost.oaaas.model.AuthorizationRequest;
import com.aerofs.bifrost.oaaas.repository.AuthorizationRequestRepository;

/**
 */
public class AuthorizationRequestRepositoryImpl implements AuthorizationRequestRepository
{
    @Override
    public AuthorizationRequest findByAuthState(String authState)
    {
        return _dao.findByAuthState(authState);
    }

    @Override
    public AuthorizationRequest findByAuthorizationCode(String authCode)
    {
        return _dao.findByAuthCode(authCode);
    }

    @Override
    public <S extends AuthorizationRequest> S save(S s) { return _dao.save(s); }

    @Override
    public void delete(AuthorizationRequest authReq) { _dao.delete(authReq); }

    @Inject private AuthorizationRequestDAO _dao;

}
