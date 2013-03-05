/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.cert;

import com.aerofs.base.id.DID;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_CERT_DEVICE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_CERT_EXPIRE_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_CERT_REVOKE_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_CERT_SERIAL;
import static com.aerofs.sp.server.lib.SPSchema.T_CERT;

public class CertificateDatabase extends AbstractSQLDatabase
{
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
        try {
            PreparedStatement ps = prepareStatement("insert into " + T_CERT +
                    "(" + C_CERT_SERIAL + "," + C_CERT_DEVICE_ID + "," + C_CERT_EXPIRE_TS +
                    ") values (?,?,?)");

            ps.setString(1, String.valueOf(serial));
            ps.setString(2, did.toStringFormal());
            ps.setTimestamp(3, new Timestamp(expiry.getTime()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "certificate serial number already exists");
            throw e;
        }
    }

    public void revokeCertificateBySerial(Long serial)
            throws SQLException
    {
        ImmutableList.Builder<Long> builder = ImmutableList.builder();
        builder.add(serial);

        revokeCertificatesBySerials(builder.build());
    }

    public void revokeCertificatesBySerials(ImmutableList<Long> serials)
            throws SQLException
    {
        // Update the revoke timestamp in the certificate table.
        PreparedStatement ps = prepareStatement("update "
                + T_CERT + " set " + C_CERT_REVOKE_TS + " = current_timestamp, " +
                C_CERT_EXPIRE_TS + " = " + C_CERT_EXPIRE_TS + " where " + C_CERT_REVOKE_TS +
                " = 0 and " + C_CERT_SERIAL + " = ?");

        for (Long serial : serials) {
            ps.setLong(1, serial);
            ps.addBatch();
        }

        // Blindly execute, since revocation of the same device twice is allowed.
        ps.executeBatch();
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
        PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_CERT,
                C_CERT_EXPIRE_TS + " > current_timestamp and " + C_CERT_REVOKE_TS + " != 0",
                C_CERT_SERIAL));

        ResultSet rs = ps.executeQuery();
        try {
            Builder<Long> builder = ImmutableList.builder();
            while (rs.next()) {
                builder.add(rs.getLong(1));
            }
            return builder.build();
        } finally {
            rs.close();
        }
    }

    /**
     * Return true if the certificate with the given serial number is revoked, false otherwise.
     */
    public boolean isRevoked(Long serial)
        throws SQLException
    {
        // Require the > 0 because the default value for a timestamp in mysql is 0, not null.
        PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_CERT,
                C_CERT_SERIAL + " =? and " + C_CERT_REVOKE_TS + " > 0",
                C_CERT_REVOKE_TS));

        ps.setLong(1, serial);
        ResultSet rs = ps.executeQuery();

        try {
            return rs.next();
        } finally {
            rs.close();
        }
    }

    public long getSerial(DID did)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_CERT, C_CERT_DEVICE_ID + "=?",
                C_CERT_SERIAL));
        ps.setString(1, did.toStringFormal());

        ResultSet rs = ps.executeQuery();
        try {
            if (!rs.next()) {
                rs.close();
                throw new ExNotFound("device " + did);
            } else {
                return rs.getLong(1);
            }
        } finally {
            assert !rs.next();
            rs.close();
        }
    }
}
