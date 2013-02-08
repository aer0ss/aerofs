package com.aerofs.sp.server.lib;

import java.util.Arrays;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.S;
import com.aerofs.base.id.UserID;

import com.aerofs.lib.db.DBUtil;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.Base64;
import com.aerofs.lib.Util;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.google.common.collect.Sets;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Set;

import static com.aerofs.sp.server.lib.SPSchema.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// TODO (WW) move ALL methods to appropriate classes.
// DO NOT ADD NEW METHODS.
public class SPDatabase extends AbstractSQLDatabase
{
    public SPDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public void insertPasswordResetToken(UserID userId, String token)
        throws SQLException
    {
        PreparedStatement ps = prepareStatement("insert into " +
                T_PASSWORD_RESET + "(" + C_PASS_TOKEN + "," + C_PASS_USER + ") values (?,?)");

        ps.setString(1, token);
        ps.setString(2, userId.toString());
        Util.verify(ps.executeUpdate() == 1);
    }

    public UserID resolvePasswordResetToken(String token)
        throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement("select " + C_PASS_USER +
                " from " + T_PASSWORD_RESET + " where " + C_PASS_TOKEN + "=? and " + C_PASS_TS +
                " > ?");

        ps.setString(1, token);
        java.util.Date today = new java.util.Date();

        ps.setTimestamp(2,
                new Timestamp(today.getTime() - SPParam.PASSWORD_RESET_TOKEN_VALID_DURATION));
        ResultSet rs = ps.executeQuery();
        try {
            if (rs.next()) {
                UserID id = UserID.fromInternal(rs.getString(1));
                assert !rs.next();
                return id;
            } else {
                throw new ExNotFound();
            }
        } finally {
            rs.close();
        }
    }

    public void deletePasswordResetToken(String token)
        throws SQLException
    {
        PreparedStatement psDPRT = prepareStatement("delete from " + T_PASSWORD_RESET +
                " where " + C_PASS_TOKEN + " = ?");

        psDPRT.setString(1, token);
        int updates = psDPRT.executeUpdate();
        Util.verify(updates == 1);
    }

    public void updateUserCredentials(UserID userId, byte[] credentials)
        throws SQLException
    {
        PreparedStatement psUUC = prepareStatement("update " + T_USER + " set " +
                C_USER_CREDS + "=? where " + C_USER_ID + "=?");

        psUUC.setString(1, Base64.encodeBytes(credentials));
        psUUC.setString(2, userId.toString());
        Util.verify(psUUC.executeUpdate() == 1);
    }

    public void checkAndUpdateUserCredentials(UserID userID, byte[] oldCredentials,
            byte[] newCredentials)
            throws ExNoPerm, SQLException
    {
        PreparedStatement psTASUC = getConnection().prepareCall(
                "update " + T_USER + " set " + C_USER_CREDS +
                        "=? where " + C_USER_ID + "=? AND " + C_USER_CREDS + "=?");

        psTASUC.setString(1, Base64.encodeBytes(newCredentials));
        psTASUC.setString(2, userID.toString());
        psTASUC.setString(3, Base64.encodeBytes(oldCredentials));
        int updated = psTASUC.executeUpdate();
        if (updated == 0) {
            throw new ExNoPerm();
        }
    }

    // TODO (WW) use DeviceRow. query user names using a separate query
    public static class DeviceInfo
    {
        public UserID _ownerID;
        public String _ownerFirstName;
        public String _ownerLastName;
        public String _deviceName;
    }

    /**
     * TODO (WW) refactor this method. move it to DeviceDatabase
     * Get the device info for a given device ID.
     *
     * Note: we're not using the getDevice() method here because it does not include the first and
     * last name. I don't want to add first and last name to that call because it will slow things
     * down and is not required in the other places that user getDevice().
     *
     * @param did the device ID we are going to search for.
     * @return the device info corresponding to the supplied device ID. If no such device exists,
     * then return null.
     */
    public @Nullable DeviceInfo getDeviceInfo(DID did) throws SQLException
    {
        // Need to join the user and the device table.
        PreparedStatement psGDI = prepareStatement(
                "select dev." + C_DEVICE_NAME + ", dev." + C_DEVICE_OWNER_ID + ", user." +
                C_USER_FIRST_NAME + ", user." + C_USER_LAST_NAME + " from " + T_DEVICE +
                " dev join " + T_USER + " user on dev." + C_DEVICE_OWNER_ID + " = user." +
                C_USER_ID + " where dev." + C_DEVICE_ID + " = ?"
        );

        psGDI.setString(1, did.toStringFormal());

        ResultSet rs = psGDI.executeQuery();
        try {
            if (rs.next()) {
                DeviceInfo di = new DeviceInfo();
                di._deviceName = rs.getString(1);
                di._ownerID = UserID.fromInternal(rs.getString(2));
                di._ownerFirstName = rs.getString(3);
                di._ownerLastName = rs.getString(4);
                return di;
            } else {
                return null;
            }
        } finally {
            rs.close();
        }
    }

    /**
     * Get the shared users set for a given user (i.e. the set of users that the supplied user
     * shares with).
     *
     * The smallest possible set is of size 1, since we say a user always shared with themselves
     * (even if there is no explicit ACL entry, i.e. even if they do not use shared folders).
     *
     * @param userId the user whose shared user set we are going to compute.
     */
    public Set<UserID> getSharedUsersSet(UserID userId) throws SQLException
    {
        Set<UserID> result = Sets.newHashSet();

        // The user always shares with themselves.
        result.add(userId);

        PreparedStatement psGSUS = prepareStatement(
                "select distinct t1." + C_AC_USER_ID + " from " + T_AC +
                        " t1 join " + T_AC +
                        " t2 on t1." + C_AC_STORE_ID + " = t2." + C_AC_STORE_ID +
                        " where t2." +
                        C_AC_USER_ID + " = ?");

        psGSUS.setString(1, userId.toString());

        ResultSet rs = psGSUS.executeQuery();
        try {
            while (rs.next()) {
                UserID sharedUser = UserID.fromInternal(rs.getString(1));
                result.add(sharedUser);
            }
        } finally {
            rs.close();
        }

        return result;
    }

    /**
     * TODO (WW) remove this class and use Device and User classes instead.
     * A class to hold both a username and a device ID.
     */
    public static class UserDevice
    {
        public final DID _did;
        public final UserID _userId;

        public UserDevice(DID did, UserID userId)
        {
            _did = did;
            _userId = userId;
        }

        @Override
        public int hashCode()
        {
            HashCodeBuilder builder = new HashCodeBuilder();
            builder.append(BaseUtil.hexEncode(_did.getBytes()));
            builder.append(_userId);
            return builder.toHashCode();
        }

        @Override
        public boolean equals(Object o)
        {
            if (!(o instanceof UserDevice)) return false;
            UserDevice od = (UserDevice)o;
            return Arrays.equals(_did.getBytes(), od._did.getBytes()) && _userId.equals(od._userId);
        }
    }

    /**
     * Get the interested devices set for a given SID belonging to a specific owner (i.e. the set
     * of devices that sync with a particular shared folder).
     *
     * Note that all the devices belonging to the owner are always included in the interested
     * devices set (regardless of exclusion).
     */
    public Set<UserDevice> getInterestedDevicesSet(SID sid, UserID ownerId)
            throws SQLException, ExFormatError
    {
        Set<UserDevice> result = Sets.newHashSet();

        PreparedStatement ps = prepareStatement(
                "select " + C_DEVICE_ID + ", " + C_DEVICE_OWNER_ID + " from " + T_AC +
                        " acl join " + T_DEVICE + " dev on " + C_AC_USER_ID + " = " +
                        C_DEVICE_OWNER_ID + " where " + C_AC_STORE_ID + " = ?");
        ps.setBytes(1, sid.getBytes());
        ResultSet rs = ps.executeQuery();
        try {
            while (rs.next()) {
                // TODO (MP) why do we store did's as CHAR(32) instead of BINARY(16)?
                String did = rs.getString(1);
                UserID userId = UserID.fromInternal(rs.getString(2));
                UserDevice ud = new UserDevice(new DID(did), userId);

                result.add(ud);
            }
        } finally {
            rs.close();
        }

        PreparedStatement psGIDSDevice = prepareStatement(
                "select " + C_DEVICE_ID + " from " + T_DEVICE + " where " +
                        C_DEVICE_OWNER_ID + " = ?");
        psGIDSDevice.setString(1, ownerId.toString());
        rs = psGIDSDevice.executeQuery();
        try {
            while (rs.next()) {
                // TODO (MP) ditto here.
                String did = rs.getString(1);
                UserDevice ud = new UserDevice(new DID(did), ownerId);
                result.add(ud);
            }
        } finally {
            rs.close();
        }

        return result;
    }

    /**
     * @param tsc the invitation code
     */
    public @Nonnull UserID getSignUpCode(String tsc)
        throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_TI, C_TI_TIC + "=?", C_TI_TO));

        ps.setString(1, tsc);
        ResultSet rs = ps.executeQuery();
        try {
            if (rs.next()) {
                UserID result = UserID.fromInternal(rs.getString(1));
                assert !rs.next();
                return result;
            } else {
                throw new ExNotFound(S.INVITATION_CODE_NOT_FOUND);
            }
        } finally {
            rs.close();
        }
    }
}
