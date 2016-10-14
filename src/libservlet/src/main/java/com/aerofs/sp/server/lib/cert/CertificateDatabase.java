/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.cert;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

import static com.aerofs.sp.server.lib.SPSchema.C_CERT_DEVICE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_CERT_EXPIRE_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_CERT_REVOKE_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_CERT_SERIAL;
import static com.aerofs.sp.server.lib.SPSchema.T_CERT;

public class CertificateDatabase extends AbstractSQLDatabase
{
    @Inject
    public CertificateDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    /**
     * @param serial the serial number of this new certificate.
     * @param did the device which owns this certificate.
     * @param expiry the date (in the future) at which this certificate expires.
     */
    public void insertCertificate(long serial, DID did, Date expiry)
            throws SQLException, ExAlreadyExist
    {
        try (PreparedStatement ps = prepareStatement("insert into " + T_CERT +
                "(" + C_CERT_SERIAL + "," + C_CERT_DEVICE_ID + "," + C_CERT_EXPIRE_TS +
                ") values (?,?,?)")) {

            ps.setString(1, String.valueOf(serial));
            ps.setString(2, did.toStringFormal());
            ps.setTimestamp(3, new Timestamp(expiry.getTime()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "certificate serial number already exists: " + serial);
            throw e;
        }
    }

    public void revokeCertificate(long serial)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement("update "
                + T_CERT + " set " + C_CERT_REVOKE_TS + " = current_timestamp"
                + " where " + C_CERT_SERIAL + "=?")) {
            ps.setLong(1, serial);
            ps.executeUpdate();
        }
    }

    /**
     * Get a a list of revoked certificate serial numbers. The returned certificates have an
     * expiry date that is in the future.
     *
     * @return list of revoked certificates.
     */
    public ImmutableList<Long> getCRL()
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_CERT,
                C_CERT_EXPIRE_TS + " > current_timestamp and " + C_CERT_REVOKE_TS + " != 0",
                C_CERT_SERIAL));
             ResultSet rs = ps.executeQuery()) {
            Builder<Long> builder = ImmutableList.builder();
            while (rs.next()) {
                builder.add(rs.getLong(1));
            }
            return builder.build();
        }
    }

    public ImmutableList<Long> getAllSerialsIssuedFor(DID did)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_CERT,
                C_CERT_DEVICE_ID + " = ?", C_CERT_SERIAL ))) {
            ps.setString(1, did.toStringFormal());
            try (ResultSet rs = ps.executeQuery()) {
                Builder<Long> builder = ImmutableList.builder();
                while (rs.next()) {
                    builder.add(rs.getLong(1));
                }
                return builder.build();
            }
        }
    }

    public DID getDid(long serial)
            throws SQLException, ExNotFound, ExInvalidID
    {
        try (PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_CERT,
                C_CERT_SERIAL + " = ?", C_CERT_DEVICE_ID))) {
            ps.setLong(1, serial);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ExNotFound();
                }
                return new DID(rs.getString(1));
            }
        }
    }

    public boolean isRevoked(long serial)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(
                DBUtil.selectWhere(T_CERT, C_CERT_SERIAL + " =?", C_CERT_REVOKE_TS))) {
            ps.setLong(1, serial);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ExNotFound();
                }
                long revokeTime = rs.getLong(1);
                // If the revoke timestamp is greater than 0, then the certificate has been revoked.
                return revokeTime > 0;
            }
        }
    }
}
