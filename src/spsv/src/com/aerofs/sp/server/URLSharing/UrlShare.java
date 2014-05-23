/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.URLSharing;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.RestObject;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLException;

/**
 * This class represents a mapping of URL key => (token, SOID, expiry, password)
 *
 * See docs/design/url_sharing.md for details
 */
public class UrlShare
{
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

    public void removeExpires(String newToken)
            throws SQLException, ExNotFound
    {
        _f._db.removeExpiresAndSetToken(_key, newToken);
    }
}

