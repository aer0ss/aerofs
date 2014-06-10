/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.URLSharing;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseSecUtil.KeyDerivation;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.RestObject;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Sp.PBRestObjectUrl;
import com.aerofs.sp.server.URLSharing.UrlSharingDatabase.HashedPasswordAndSalt;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.lambdaworks.crypto.SCrypt;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Collection;

/**
 * This class represents a mapping of URL key => (token, SOID, expiry, password)
 *
 * See docs/design/url_sharing.md for details
 */
public class UrlShare
{
    public static int PASSWORD_SALT_LENGTH = 16;  // must match db column

    public static String generateKey()
    {
        return UniqueID.generate().toStringFormal();
    }

    public static class Factory
    {
        private UrlSharingDatabase _db;

        @Inject
        public Factory(UrlSharingDatabase db)
        {
            _db = db;
        }

        public UrlShare create(@Nonnull String key)
        {
            return new UrlShare(this, key);
        }

        /**
         * Create and return a new UrlShare object with a random unique key
         */
        public UrlShare save(@Nonnull RestObject restObject, @Nonnull String token,
                @Nonnull UserID createdBy)
                throws SQLException
        {
            Preconditions.checkArgument(restObject.getSID() != null);
            Preconditions.checkArgument(restObject.getOID() != null);
            while (true) {
                String key = generateKey();
                try {
                    _db.insertRow(key, restObject.getSID(), restObject.getOID(), token, null,
                            createdBy);
                    return create(key);
                } catch (ExAlreadyExist ignored) {
                    // try again
                }
            }
        }

        public @Nonnull Collection<UrlShare> getAllInStore(@Nonnull SID sid)
                throws SQLException
        {
            Collection<String> keys = _db.getKeysInStore(sid);
            Collection<UrlShare> urlShares = Lists.newArrayListWithCapacity(keys.size());
            for (String key : keys) urlShares.add(new UrlShare(this, key));
            return urlShares;
        }
    }

    private final @Nonnull Factory _f;
    private final @Nonnull String _key;

    private UrlShare(@Nonnull Factory f, @Nonnull String key)
    {
        _f = f;
        _key = key;
    }

    public @Nonnull RestObject getRestObject()
            throws SQLException, ExNotFound
    {
        SID sid = _f._db.getSid(_key);
        OID oid = _f._db.getOid(_key);
        return new RestObject(sid, oid);
    }

    public @Nonnull String getKey()
    {
        return _key;
    }

    public @Nonnull SID getSid()
            throws SQLException, ExNotFound
    {
        return _f._db.getSid(_key);
    }

    public @Nonnull String getToken()
            throws SQLException, ExNotFound
    {
        return _f._db.getToken(_key);
    }

    public @Nullable Long getExpiresNullable()
            throws SQLException, ExNotFound
    {
        return _f._db.getExpires(_key);
    }

    public @Nonnull UserID getCreatedBy()
            throws SQLException, ExNotFound
    {
        String userid = _f._db.getCreatedBy(_key);
        return UserID.fromInternal(userid);
    }

    public void setExpires(long expires, @Nonnull String newToken)
            throws SQLException, ExNotFound
    {
        _f._db.setExpiresAndToken(_key, expires, newToken);
    }

    public void removeExpires(@Nonnull String newToken)
            throws SQLException, ExNotFound
    {
        _f._db.removeExpiresAndSetToken(_key, newToken);
    }

    public void delete()
            throws SQLException, ExNotFound
    {
        _f._db.removeRow(_key);
    }

    /*
     * N.B. a new token is required because otherwise, users who had accessed
     * the link before setPassword was called could still access content by
     * using the old token directly. We therefore replace the token with a
     * new one, and require the caller to invalidate the old token.
     */
    public void setPassword(@Nonnull byte[] password, @Nonnull String newToken)
            throws SQLException, ExNotFound, GeneralSecurityException
    {
        byte[] salt = BaseSecUtil.newRandomBytes(PASSWORD_SALT_LENGTH);
        byte[] hash = SCrypt.scrypt(password, salt, KeyDerivation.N, KeyDerivation.r,
                KeyDerivation.p, KeyDerivation.dkLen);
        _f._db.setPasswordAndToken(_key, hash, salt, newToken);
    }

    public void removePassword()
            throws SQLException, ExNotFound
    {
        _f._db.removePassword(_key);
    }

    public void validatePassword(@Nonnull byte[] password)
            throws SQLException, ExNotFound, ExBadCredential, GeneralSecurityException
    {
        HashedPasswordAndSalt hashedPasswordAndSalt = _f._db.getHashedPasswordAndSalt(_key);
        if (hashedPasswordAndSalt == null) throw new ExBadCredential("no password is set");
        byte[] candidate = SCrypt.scrypt(password, hashedPasswordAndSalt.salt, KeyDerivation.N,
                KeyDerivation.r, KeyDerivation.p, KeyDerivation.dkLen);
        if (!BaseSecUtil.constantTimeIsEqual(hashedPasswordAndSalt.hash, candidate)) {
            throw new ExBadCredential();
        }
    }

    public boolean hasPassword()
            throws SQLException, ExNotFound
    {
        HashedPasswordAndSalt hashedPasswordAndSalt = _f._db.getHashedPasswordAndSalt(_key);
        return (hashedPasswordAndSalt != null);
    }

    public PBRestObjectUrl toPB()
            throws SQLException, ExNotFound
    {
        PBRestObjectUrl.Builder builder = PBRestObjectUrl.newBuilder()
                .setKey(_key)
                .setSoid(getRestObject().toStringFormal())
                .setCreatedBy(getCreatedBy().getString())
                .setHasPassword(hasPassword())
                .setToken(getToken());
        Long expires = getExpiresNullable();
        if (expires != null) builder.setExpires(expires);
        return builder.build();
    }
}

