/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.cert;

import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UserID;
import com.aerofs.servlets.lib.db.AbstractSQLDatabase;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

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
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_OWNER_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_CERT;
import static com.aerofs.sp.server.lib.SPSchema.T_DEVICE;

/**
 * TODO (WW) refactor this class. and create a Certificate class as a application-level wrapper?
 */
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
    public void addCertificate(long serial, DID did, Date expiry)
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
            throwOnConstraintViolation(e);
            throw e;
        }
    }

    /**
     * Revoke all the certificates belonging to a single device.
     *
     * Important note: this should be called within a transaction!
     *
     * @param did the device whose certificates we are going to revoke.
     */
    public ImmutableList<Long> revokeDeviceCertificates(final DID did)
            throws SQLException
    {
        // Find the affected serial in the certificate table.
        PreparedStatement ps = prepareStatement("select " +
                C_CERT_SERIAL + " from " + T_CERT + " where " + C_CERT_DEVICE_ID +
                " = ? and " + C_CERT_REVOKE_TS + " = 0");

        ps.setString(1, did.toStringFormal());

        ResultSet rs = ps.executeQuery();
        try {
            Builder<Long> builder = ImmutableList.builder();

            // Sigh... result set does not have a size member.
            int count = 0;
            while (rs.next()) {
                builder.add(rs.getLong(1));
                count++;
            }

            // Verify that indeed we only have one device cert.
            assert count == 0 || count == 1 : ("too many device certs: " + count);

            ImmutableList<Long> serials = builder.build();
            revokeCertificatesBySerials(serials);
            return serials;
        } finally {
            rs.close();
        }
    }

    /**
     * Revoke all certificates belonging to user.
     *
     * Important note: this should be called within a transaction!
     *
     * @param userId the user whose certificates we are going to revoke.
     */
    public ImmutableList<Long> revokeUserCertificates(UserID userId)
            throws SQLException
    {
        // Find all unrevoked serials for the device.
        PreparedStatement ps = prepareStatement("select " +
                C_CERT_SERIAL + " from " + T_CERT + " " + "join " + T_DEVICE + " on " +
                T_CERT + "." + C_CERT_DEVICE_ID + " = " + T_DEVICE + "." + C_DEVICE_ID +
                " where " + T_DEVICE + "." + C_DEVICE_OWNER_ID + " = ? and " +
                C_CERT_REVOKE_TS + " = 0");

        ps.setString(1, userId.toString());

        ResultSet rs = ps.executeQuery();
        try {
            Builder<Long> builder = ImmutableList.builder();

            while (rs.next()) {
                builder.add(rs.getLong(1));
            }

            ImmutableList<Long> serials = builder.build();
            revokeCertificatesBySerials(serials);

            return serials;
        } finally {
            rs.close();
        }
    }

    private void revokeCertificatesBySerials(ImmutableList<Long> serials)
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

        executeBatchWarn(ps, serials.size(), 1);
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
        PreparedStatement ps = prepareStatement("select " + C_CERT_SERIAL +
                " from " + T_CERT + " where " + C_CERT_EXPIRE_TS + " > current_timestamp and " +
                C_CERT_REVOKE_TS + " != 0");

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
}
