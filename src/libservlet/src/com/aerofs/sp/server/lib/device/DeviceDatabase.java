/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.device;

import com.aerofs.ids.DID;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExDeviceIDAlreadyExists;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.UserID;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;

import javax.annotation.Nonnull;
import javax.inject.Inject;
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
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_UNLINKED;
import static com.aerofs.sp.server.lib.SPSchema.T_DEVICE;

/**
 * N.B. only Device.java may refer to this class
 */
public class DeviceDatabase extends AbstractSQLDatabase
{
    @Inject
    public DeviceDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public void insertDevice(DID did, UserID ownerID, String osFamily, String osName,
            String deviceName)
            throws SQLException, ExDeviceIDAlreadyExists
    {
        try (PreparedStatement ps = prepareStatement(
                    DBUtil.insert(T_DEVICE, C_DEVICE_ID, C_DEVICE_OWNER_ID, C_DEVICE_NAME,
                            C_DEVICE_OS_FAMILY, C_DEVICE_OS_NAME))) {

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

    public void markUnlinked(DID did)
            throws SQLException
    {
        try (PreparedStatement psUpdateUnlinkedFlag = prepareStatement(DBUtil.updateWhere(T_DEVICE,
                C_DEVICE_ID + "=?", C_DEVICE_UNLINKED))) {

            psUpdateUnlinkedFlag.setBoolean(1, true);
            psUpdateUnlinkedFlag.setString(2, did.toStringFormal());

            psUpdateUnlinkedFlag.executeUpdate();
        }
    }

    public boolean hasDevice(DID did)
            throws SQLException
    {
        try (PreparedStatement ps = prepareStatement(selectWhere(T_DEVICE, C_DEVICE_ID + "=?",
                "count(*)"))) {
            ps.setString(1, did.toStringFormal());
            try (ResultSet rs = ps.executeQuery()) {
                Util.verify(rs.next());
                int count = rs.getInt(1);
                assert count == 0 || count == 1;
                assert !rs.next();
                return count != 0;
            }
        }
    }

    public @Nonnull UserID getOwnerID(DID did)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = queryDevice(did, C_DEVICE_OWNER_ID);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, did);
            return UserID.fromInternal(rs.getString(1));
        }
    }

    public @Nonnull String getName(DID did)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = queryDevice(did, C_DEVICE_NAME);
             ResultSet rs = ps.executeQuery()) {
             throwIfEmptyResultSet(rs, did);
             return rs.getString(1);
        }
    }

    public @Nonnull String getOSFamily(DID did)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = queryDevice(did, C_DEVICE_OS_FAMILY);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, did);
            return rs.getString(1);
        }
    }

    public long getInstallDate(DID did)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = queryDevice(did, C_DEVICE_TS);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, did);
            return rs.getTimestamp(1).getNanos();
        }
    }

    public @Nonnull String getOSName(DID did)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = queryDevice(did, C_DEVICE_OS_NAME);
             ResultSet rs = ps.executeQuery()) {
            throwIfEmptyResultSet(rs, did);
            return rs.getString(1);
        }
    }

    /**
     * Trim the name before saving it to the db
     */
    public void setName(DID did, String name)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(
                updateWhere(T_DEVICE, C_DEVICE_ID + "=?", C_DEVICE_NAME))) {

            ps.setString(1, name.trim());
            ps.setString(2, did.toStringFormal());

            int count = ps.executeUpdate();
            assert count <= 1;
            if (count == 0) throw new ExNotFound("device " + did.toString());
        }
    }

    public void setOSFamilyAndName(DID deviceId, String osFamily, String osName)
            throws SQLException, ExNotFound
    {
        try (PreparedStatement ps = prepareStatement(
                updateWhere(T_DEVICE, C_DEVICE_ID + "=?", C_DEVICE_OS_FAMILY, C_DEVICE_OS_NAME))) {

            ps.setString(1, osFamily);
            ps.setString(2, osName);
            ps.setString(3, deviceId.toStringFormal());

            int count = ps.executeUpdate();
            assert count <= 1;
            if (count == 0) throw new ExNotFound("device " + deviceId);
        }
    }

    private PreparedStatement queryDevice(DID did, String... fields)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_DEVICE, C_DEVICE_ID + "=?", fields));
        ps.setString(1, did.toStringFormal());
        return ps;
    }

    // N.B. will return with cursor on the first element of the result set
    private void throwIfEmptyResultSet(ResultSet rs, DID did)
            throws SQLException, ExNotFound
    {
        if (!rs.next()) {
            throw new ExNotFound("device " + did);
        }
    }
}
