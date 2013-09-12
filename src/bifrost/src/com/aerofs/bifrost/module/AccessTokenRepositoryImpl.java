/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.module;

import com.google.inject.Inject;
import com.aerofs.bifrost.oaaas.model.AccessToken;
import com.aerofs.bifrost.oaaas.repository.AccessTokenRepository;


/**
 */
public class AccessTokenRepositoryImpl implements AccessTokenRepository
{
    @Override
    public AccessToken findByToken(String token) { return _dao.findByToken(token); }

    @Override
    public <S extends AccessToken> S save(S s) { return _dao.save(s); }

    @Override
    public void delete(AccessToken accessToken)
    {

    }

    @Inject private AccessTokenDAO _dao;

    /*
     * we do not use refresh tokens at this time
     */

    @Override
    public AccessToken findByRefreshToken(String refreshToken) { throw new UnsupportedOperationException(); }
}
