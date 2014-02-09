/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.cert;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
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

import static com.aerofs.lib.db.DBUtil.binaryCount;
import static com.aerofs.lib.db.DBUtil.selectWhere;
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
        try {
            PreparedStatement ps = prepareStatement("insert into " + T_CERT +
                    "(" + C_CERT_SERIAL + "," + C_CERT_DEVICE_ID + "," + C_CERT_EXPIRE_TS +
                    ") values (?,?,?)");

            ps.setString(1, String.valueOf(serial));
            ps.setString(2, did.toStringFormal());
            ps.setTimestamp(3, new Timestamp(expiry.getTime()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throwOnConstraintViolation(e, "certificate serial number already exists: " + serial);
            throw e;
        }
    }

    public void revokeCertificate(DID did)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement("update "
                + T_CERT + " set " + C_CERT_REVOKE_TS + " = current_timestamp, " +
                C_CERT_EXPIRE_TS + " = " + C_CERT_EXPIRE_TS + " where " + C_CERT_REVOKE_TS +
                " = 0 and " + C_CERT_DEVICE_ID + " = ?");

        ps.setString(1, did.toStringFormal());
        ps.execute();
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

    public boolean isRevoked(DID did)
        throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.selectWhere(T_CERT, C_CERT_DEVICE_ID + " =?", C_CERT_REVOKE_TS));

        ps.setString(1, did.toStringFormal());
        ResultSet rs = ps.executeQuery();

        try {
            if (!rs.next()) {
                throw new ExNotFound();
            }

            long revokeTime = rs.getLong(1);
            // If the revoke timestamp is greater than 0, then the certificate has been revoked.
            return revokeTime > 0;
        } finally {
            rs.close();
        }
    }

    /**
     * Get the serial number for a given device.
     *
     * @return the serial number of the most recent certificate associated with the specified
     * device, or INVALID_SERIAL if a certificate does not exist for this DID.
     */
    public long getSerial(DID did)
            throws SQLException, ExNotFound
    {
        // We want the most *recent* cert associated with this DID.  It's possible that we
        // know about multiple certificates, as devices may renew their certificates over
        // time.  However, CA serial numbers are guaranteed to be increasing for the entire life of
        // the product.
        PreparedStatement ps = prepareStatement(selectWhere(T_CERT, C_CERT_DEVICE_ID + "=?",
                C_CERT_SERIAL) + " order by " + C_CERT_SERIAL + " desc");
        ps.setString(1, did.toStringFormal());

        ResultSet rs = ps.executeQuery();
        try {
            if (!rs.next()) {
                throw new ExNotFound("device " + did);
            } else {
                return rs.getLong(1);
            }
        } finally {
            assert !rs.next();
            rs.close();
        }
    }

    public boolean exists(DID did)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_CERT, C_CERT_DEVICE_ID + "=?",
                "count(*)"));
        ps.setString(1, did.toStringFormal());
        ResultSet rs = ps.executeQuery();
        try {
            return binaryCount(rs);
        } finally {
            rs.close();
        }
    }
}
