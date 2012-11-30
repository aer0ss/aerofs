/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.device;

import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UserID;
import com.aerofs.servlets.lib.db.AbstractSQLDatabase;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.lib.db.DBUtil.updateWhere;
import static com.aerofs.sp.server.lib.SPSchema.CONSTRAINT_DEVICE_NAME_OWNER;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_NAME;
import static com.aerofs.sp.server.lib.SPSchema.C_DEVICE_OWNER_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_DEVICE;
import com.aerofs.sp.server.lib.device.Device.ExDeviceNameAlreadyExist;
import com.aerofs.sp.server.lib.device.Device.ExDeviceIDAlreadyExist;

/**
 * N.B. only User.java may refer to this class
 */
public class DeviceDatabase extends AbstractSQLDatabase
{
    public DeviceDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public void addDevice(DID did, UserID ownerID, String name)
            throws SQLException, ExDeviceNameAlreadyExist, ExDeviceIDAlreadyExist
    {
        try {
            PreparedStatement ps = prepareStatement(
                    DBUtil.insert(T_DEVICE, C_DEVICE_ID, C_DEVICE_OWNER_ID, C_DEVICE_NAME));

            ps.setString(1, did.toStringFormal());
            ps.setString(2, ownerID.toString());
            ps.setString(3, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throwOnDeviceIDOrNameConstraintViolation(e);
        }
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

    public void setName(DID did, String name)
            throws SQLException, ExDeviceNameAlreadyExist, ExNotFound
    {
        PreparedStatement ps = prepareStatement(
                updateWhere(T_DEVICE, C_DEVICE_ID + "=?", C_DEVICE_NAME));

        ps.setString(1, name);
        ps.setString(2, did.toStringFormal());

        try {
            int count = ps.executeUpdate();
            assert count <= 1;
            if (count == 0) throw new ExNotFound("device " + did.toString());
        } catch (SQLException e) {
            throwOnDeviceNameConstraintViolation(e);
        }

    }

    private static void throwOnDeviceIDOrNameConstraintViolation(SQLException e)
            throws ExDeviceNameAlreadyExist, ExDeviceIDAlreadyExist, SQLException
    {
        if (isConstraintViolation(e)) {
            if (e.getMessage().contains(CONSTRAINT_DEVICE_NAME_OWNER)) {
                throw new ExDeviceNameAlreadyExist();
            } else if (e.getMessage().contains(C_DEVICE_ID)) {
                throw new ExDeviceIDAlreadyExist();
            } else {
                assert false;
            }
        } else {
            throw e;
        }
    }

    private static void throwOnDeviceNameConstraintViolation(SQLException e)
            throws ExDeviceNameAlreadyExist, SQLException
    {
        if (isConstraintViolation(e)) {
            if (e.getMessage().contains(CONSTRAINT_DEVICE_NAME_OWNER)) {
                throw new ExDeviceNameAlreadyExist();
            } else {
                assert false;
            }
        } else {
            throw e;
        }
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
