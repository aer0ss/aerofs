/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.url_sharing;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseSecUtil.KeyDerivation;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.OID;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.proto.Sp.PBRestObjectUrl;
import com.aerofs.sp.server.url_sharing.UrlSharingDatabase.HashedPasswordAndSalt;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.lambdaworks.crypto.SCrypt;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Collection;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;

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
        private final UrlSharingDatabase _db;

        // Whether URL sharing is enabled
        private final boolean ENABLED = getBooleanProperty("url_sharing.enabled", true);
        private final boolean REQUIRE_LOGIN = getBooleanProperty("links_require_login.enabled", false);

        @Inject
        public Factory(UrlSharingDatabase db)
        {
            _db = db;
        }

        /**
         * @throws ExNoPerm if URL sharing is disabled
         */
        public UrlShare create(@Nonnull String key)
                throws ExNoPerm
        {
            if (!ENABLED) throw new ExNoPerm("URL sharing is disabled");

            return new UrlShare(this, key);
        }

        /**
         * Create and return a new UrlShare object with a random unique key.
         * @throws ExNoPerm if URL sharing is disabled
         */
        public UrlShare save(@Nonnull RestObject restObject, @Nonnull String token,
                @Nonnull UserID createdBy)
                throws SQLException, ExNoPerm
        {
            if (!ENABLED) throw new ExNoPerm("URL sharing is disabled");

            Preconditions.checkArgument(restObject.getSID() != null);
            Preconditions.checkArgument(restObject.getOID() != null);
            while (true) {
                String key = generateKey();
                try {
                    _db.insertRow(key, restObject.getSID(), restObject.getOID(), token, null,
                            REQUIRE_LOGIN, createdBy);
                    return create(key);
                } catch (ExAlreadyExist ignored) {
                    // try again
                }
            }
        }

        public @Nonnull Iterable<UrlShare> getAllInStore(@Nonnull SID sid)
                throws SQLException
        {
            Collection<String> keys = _db.getKeysInStore(sid);
            return Iterables.transform(keys, key -> new UrlShare(this, key));
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

    public boolean getRequireLogin()
        throws SQLException, ExNotFound
    {
        return _f._db.getRequireLogin(_key);
    }

    public void setRequireLogin(boolean requireLogin, @Nonnull String newToken)
            throws SQLException, ExNotFound
    {
        _f._db.setRequireLoginAndToken(_key, requireLogin, newToken);
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
                .setRequireLogin(getRequireLogin())
                .setToken(getToken());
        Long expires = getExpiresNullable();
        if (expires != null) builder.setExpires(expires);
        return builder.build();
    }
}

