/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.module;

import com.aerofs.bifrost.oaaas.model.AccessToken;
import com.aerofs.bifrost.oaaas.repository.AccessTokenRepository;
import com.google.inject.Inject;

import java.util.List;


/**
 */
public class AccessTokenRepositoryImpl implements AccessTokenRepository
{
    @Override
    public AccessToken findByToken(String token) { return _dao.findByToken(token); }

    @Override
    public AccessToken save(AccessToken s) { return _dao.save(s); }

    @Override
    public void delete(AccessToken accessToken) { _dao.delete(accessToken); }

    @Override
    public void deleteDelegatedTokensByOwner(String owner)
    {
        _dao.deleteDelegatedTokensByOwner(owner);
    }

    @Override
    public void deleteAllTokensByOwner(String owner)
    {
        _dao.deleteAllTokensByOwner(owner);
    }

    @Inject private AccessTokenDAO _dao;

    /*
     * we do not use refresh tokens at this time
     */

    @Override
    public AccessToken findByRefreshToken(String refreshToken) { throw new UnsupportedOperationException(); }

    @Override
    public List<AccessToken> findByOwner(String owner) { return _dao.findByOwner(owner); }
}
