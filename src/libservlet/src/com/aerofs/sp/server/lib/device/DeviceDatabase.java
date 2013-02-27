/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.device;

import com.aerofs.base.id.DID;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExDeviceIDAlreadyExists;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_OS_FAMILY;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_OS_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_OWNER_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_DEVICE;

/**
 * N.B. only User.java may refer to this class
 */
public class DeviceDatabase extends AbstractSQLDatabase
{
    public DeviceDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public void insertDevice(DID did, UserID ownerID, String osFamily, String osName,
            String deviceName)
            throws SQLException, ExDeviceIDAlreadyExists
    {
        try {
            PreparedStatement ps = prepareStatement(
                    DBUtil.insert(T_DEVICE, C_DEVICE_ID, C_DEVICE_OWNER_ID, C_DEVICE_NAME,
                            C_DEVICE_OS_FAMILY, C_DEVICE_OS_NAME));

            ps.setString(1, did.toStringFormal());
            ps.setString(2, ownerID.getString());
            ps.setString(3, deviceName);
            ps.setString(4, osFamily);
            ps.setString(5, osName);
            ps.executeUpdate();
        } catch (SQLException e) {
            throwOnDeviceIDConstraintViolation(e);
        }
    }

    private static void throwOnDeviceIDConstraintViolation(SQLException e)
            throws ExDeviceIDAlreadyExists, SQLException
    {
        if (isConstraintViolation(e) && e.getMessage().contains("for key 'PRIMARY'")) {
            throw new ExDeviceIDAlreadyExists();
        } else {
            throw e;
        }
    }

    public void deleteDevice(DID did)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(DBUtil.deleteWhere(T_DEVICE, C_DEVICE_ID + "=?"));
        ps.setString(1, did.toStringFormal());
        ps.executeUpdate();
    }

    public boolean hasDevice(DID did)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_DEVICE, C_DEVICE_ID + "=?",
                "count(*)"));
        ps.setString(1, did.toStringFormal());
        ResultSet rs = ps.executeQuery();
        try {
            Util.verify(rs.next());
            int count = rs.getInt(1);
            assert count == 0 || count == 1;
            assert !rs.next();
            return count != 0;
        } finally {
            rs.close();
        }
    }

    public @Nonnull UserID getOwnerID(DID did)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryDevice(did, C_DEVICE_OWNER_ID);
        try {
            return UserID.fromInternal(rs.getString(1));
        } finally {
            rs.close();
        }
    }

    public @Nonnull String getName(DID did)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryDevice(did, C_DEVICE_NAME);
        try {
            return rs.getString(1);
        } finally {
            rs.close();
        }
    }

    public @Nonnull String getOSFamily(DID did)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryDevice(did, C_DEVICE_OS_FAMILY);
        try {
            return rs.getString(1);
        } finally {
            rs.close();
        }
    }

    public @Nonnull String getOSName(DID did)
            throws SQLException, ExNotFound
    {
        ResultSet rs = queryDevice(did, C_DEVICE_OS_NAME);
        try {
            return rs.getString(1);
        } finally {
            rs.close();
        }
    }

    /**
     * Trim the name before saving it to the db
     */
    public void setName(DID did, String name)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(
                updateWhere(T_DEVICE, C_DEVICE_ID + "=?", C_DEVICE_NAME));

        ps.setString(1, name.trim());
        ps.setString(2, did.toStringFormal());

        int count = ps.executeUpdate();
        assert count <= 1;
        if (count == 0) throw new ExNotFound("device " + did.toString());
    }

    public void setOSFamilyAndName(DID deviceId, String osFamily, String osName)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(
                updateWhere(T_DEVICE, C_DEVICE_ID + "=?", C_DEVICE_OS_FAMILY, C_DEVICE_OS_NAME));

        ps.setString(1, osFamily);
        ps.setString(2, osName);
        ps.setString(3, deviceId.toStringFormal());

        int count = ps.executeUpdate();
        assert count <= 1;
        if (count == 0) throw new ExNotFound("device " + deviceId);
    }

    private ResultSet queryDevice(DID did, String... fields)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_DEVICE, C_DEVICE_ID + "=?", fields));
        ps.setString(1, did.toStringFormal());
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            throw new ExNotFound("device " + did);
        } else {
            return rs;
        }
    }
}
