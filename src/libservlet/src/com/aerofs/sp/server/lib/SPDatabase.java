package com.aerofs.sp.server.lib;

import com.aerofs.base.Base64;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.UserID;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.google.common.collect.Sets;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Set;

import static com.aerofs.sp.server.lib.SPSchema.C_AC_STORE_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_AC_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_PASS_TOKEN;
import static com.aerofs.sp.server.lib.SPSchema.C_PASS_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_PASS_USER;
import static com.aerofs.sp.server.lib.SPSchema.C_SIGNUP_CODE_CODE;
import static com.aerofs.sp.server.lib.SPSchema.C_SIGNUP_CODE_TO;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_CREDS;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_AC;
import static com.aerofs.sp.server.lib.SPSchema.T_PASSWORD_RESET;
import static com.aerofs.sp.server.lib.SPSchema.T_SIGNUP_CODE;
import static com.aerofs.sp.server.lib.SPSchema.T_USER;

// TODO (WW) move ALL methods to appropriate classes.
// DO NOT ADD NEW METHODS.
public class SPDatabase extends AbstractSQLDatabase
{
    @Inject
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
        ps.setString(2, userId.getString());
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
        psUUC.setString(2, userId.getString());
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
        psTASUC.setString(2, userID.getString());
        psTASUC.setString(3, Base64.encodeBytes(oldCredentials));
        int updated = psTASUC.executeUpdate();
        if (updated == 0) {
            throw new ExNoPerm();
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

        psGSUS.setString(1, userId.getString());

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

    // TODO (WW) move to UserDatabase?
    public @Nonnull UserID getSignUpCode(String code)
        throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_SIGNUP_CODE, C_SIGNUP_CODE_CODE + "=?",
                C_SIGNUP_CODE_TO));

        ps.setString(1, code);
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
