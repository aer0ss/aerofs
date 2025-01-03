/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.url_sharing;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.List;

import static com.aerofs.sp.server.lib.SPSchema.C_US_CREATED_BY;
import static com.aerofs.sp.server.lib.SPSchema.C_US_EXPIRES;
import static com.aerofs.sp.server.lib.SPSchema.C_US_REQUIRE_LOGIN;
import static com.aerofs.sp.server.lib.SPSchema.C_US_HASHED_PASSWORD;
import static com.aerofs.sp.server.lib.SPSchema.C_US_KEY;
import static com.aerofs.sp.server.lib.SPSchema.C_US_OID;
import static com.aerofs.sp.server.lib.SPSchema.C_US_PASSWORD_SALT;
import static com.aerofs.sp.server.lib.SPSchema.C_US_SID;
import static com.aerofs.sp.server.lib.SPSchema.C_US_TOKEN;
import static com.aerofs.sp.server.lib.SPSchema.T_US;

public class UrlSharingDatabase extends AbstractSQLDatabase
{
    public static class HashedPasswordAndSalt
    {
        public @Nonnull byte[] hash;
        public @Nonnull byte[] salt;

        public HashedPasswordAndSalt(@Nonnull byte[] hash, @Nonnull byte[] salt)
        {
            this.hash = hash;
            this.salt = salt;
        }
    }

    @Inject
    public UrlSharingDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    // FIXME this function never throws ExAlreadyExist, but it should.
    public void insertRow(@Nonnull String key, @Nonnull SID sid, @Nonnull OID oid,
            @Nonnull String token, @Nullable Long expires, boolean requireLogin,
            @Nonnull UserID createdBy)
            throws SQLException, ExAlreadyExist
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.insert(T_US, C_US_KEY, C_US_SID, C_US_OID, C_US_TOKEN, C_US_EXPIRES,
                        C_US_REQUIRE_LOGIN, C_US_CREATED_BY))) {
            ps.setString(1, key);
            ps.setBytes(2, sid.getBytes());
            ps.setBytes(3, oid.getBytes());
            ps.setString(4, token);
            if (expires == null) ps.setNull(5, Types.BIGINT);
            else ps.setLong(5, expires);
            ps.setBoolean(6, requireLogin);
            ps.setString(7, createdBy.getString());

            ps.executeUpdate();
        }
    }

    public void setRequireLoginAndToken(@Nonnull String key, boolean requireLogin, @Nonnull String token)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.updateWhere(T_US, C_US_KEY + "=?", C_US_REQUIRE_LOGIN, C_US_TOKEN))) {
            ps.setBoolean (1, requireLogin);
            ps.setString(2, token);
            ps.setString(3, key);

            if (ps.executeUpdate() != 1) throw new ExNotFound();
        }
    }

    public void setExpiresAndToken(@Nonnull String key, long expires, @Nonnull String token)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.updateWhere(T_US, C_US_KEY + "=?", C_US_EXPIRES, C_US_TOKEN))) {
            ps.setLong(1, expires);
            ps.setString(2, token);
            ps.setString(3, key);

            if (ps.executeUpdate() != 1) throw new ExNotFound();
        }
    }

    public @Nonnull String getToken(@Nonnull String key)
            throws SQLException, ExNotFound
    {
        return getStringColumn(key, C_US_TOKEN);
    }

    public @Nonnull SID getSid(@Nonnull String key)
            throws SQLException, ExNotFound
    {
        byte[] sidBytes = getBytesColumn(key, C_US_SID);
        return new SID(sidBytes);
    }

    public @Nonnull OID getOid(@Nonnull String key)
            throws SQLException, ExNotFound
    {
        byte[] oidBytes = getBytesColumn(key, C_US_OID);
        return new OID(oidBytes);
    }

    public @Nonnull String getCreatedBy(@Nonnull String key)
            throws SQLException, ExNotFound
    {
        return getStringColumn(key, C_US_CREATED_BY);
    }

    public boolean getRequireLogin(@Nonnull String key)
            throws SQLException, ExNotFound
    {
        return getBooleanColumn(key, C_US_REQUIRE_LOGIN);
    }

    public @Nullable Long getExpires(@Nonnull String key)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.selectWhere(T_US, C_US_KEY + "=?", C_US_EXPIRES))) {
            ps.setString(1, key);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new ExNotFound();
                Long read = rs.getLong(1);
                return rs.wasNull() ? null : read;
            }
        }
    }

    public void removeExpiresAndSetToken(@Nonnull String key, @Nonnull String token)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.updateWhere(T_US, C_US_KEY + "=?", C_US_EXPIRES, C_US_TOKEN))) {
            ps.setNull(1, Types.BIGINT);
            ps.setString(2, token);
            ps.setString(3, key);

            if (ps.executeUpdate() != 1) throw new ExNotFound();
        }
    }

    private @Nonnull byte[] getBytesColumn(String key, String column)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_US, C_US_KEY + "=?", column))) {
            ps.setString(1, key);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBytes(1);
                else throw new ExNotFound();
            }
        }
    }

    private @Nonnull String getStringColumn(String key, String column)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_US, C_US_KEY + "=?", column))) {
            ps.setString(1, key);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
                else throw new ExNotFound();
            }
        }
    }

    private boolean getBooleanColumn(String key, String column)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_US, C_US_KEY + "=?", column))) {
            ps.setString(1, key);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBoolean(1);
                else throw new ExNotFound();
            }
        }
    }

    public void removeRow(@Nonnull String key)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.deleteWhereEquals(T_US, C_US_KEY))) {
            ps.setString(1, key);

            if (ps.executeUpdate() == 0) throw new ExNotFound();
        }
    }

    public void setPasswordAndToken(@Nonnull String key, byte[] hash, byte[] salt,
            @Nonnull String token)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.updateWhere(T_US, C_US_KEY + "=?", C_US_HASHED_PASSWORD, C_US_PASSWORD_SALT,
                        C_US_TOKEN))) {
            ps.setBytes(1, hash);
            ps.setBytes(2, salt);
            ps.setString(3, token);
            ps.setString(4, key);

            if (ps.executeUpdate() != 1) throw new ExNotFound();
        }
    }

    public void removePassword(@Nonnull String key)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.updateWhere(T_US, C_US_KEY + "=?", C_US_HASHED_PASSWORD, C_US_PASSWORD_SALT))) {
            ps.setNull(1, Types.BINARY);
            ps.setNull(2, Types.BINARY);
            ps.setString(3, key);

            if (ps.executeUpdate() != 1) throw new ExNotFound();
        }
    }

    public @Nullable
    HashedPasswordAndSalt getHashedPasswordAndSalt(@Nonnull String key)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.selectWhere(T_US, C_US_KEY + "=?", C_US_HASHED_PASSWORD, C_US_PASSWORD_SALT))) {
            ps.setString(1, key);

            byte[] hash;
            byte[] salt;

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new ExNotFound();
                hash = rs.getBytes(1);
                salt = rs.getBytes(2);
            }
            if (hash == null && salt == null) return null;
            if (hash != null && salt != null) return new HashedPasswordAndSalt(hash, salt);
            throw new IllegalStateException("hash and salt must both be present or both be null");
        }
    }

    public @Nonnull Collection<String> getKeysInStore(@Nonnull SID sid)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_US, C_US_SID + "=?", C_US_KEY))) {
            ps.setBytes(1, sid.getBytes());

            List<String> keys = Lists.newArrayList();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) keys.add(rs.getString(1));
                return keys;
            }
        }
    }

}
