/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.lib.twofactor;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.servlets.lib.db.sql.AbstractSQLDatabase;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.sp.server.lib.SPSchema.C_TF_RECOVERY_CODE;
import static com.aerofs.sp.server.lib.SPSchema.C_TF_RECOVERY_CODE_USE_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_TF_RECOVERY_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_TF_RECOVERY_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_TWO_FACTOR_SECRET;
import static com.aerofs.sp.server.lib.SPSchema.C_TWO_FACTOR_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_TWO_FACTOR_RECOVERY;
import static com.aerofs.sp.server.lib.SPSchema.T_TWO_FACTOR_SECRET;

public class TwoFactorAuthDatabase extends AbstractSQLDatabase
{
    public static int EXPECTED_RECOVERY_CODE_COUNT = 10; // Number of recovery codes per user
    public static int TWO_FACTOR_SECRET_KEY_LENGTH = 10; // 10 byte secret keys are adequate
    public static int RECOVERY_CODE_MAX_LENGTH = 10; // maximum length of a recovery code

    @Inject
    public TwoFactorAuthDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    public void prepareUser(UserID user, byte[] secret, ImmutableList<String> recoveryCodes)
            throws SQLException
    {
        Preconditions.checkArgument(secret.length == TWO_FACTOR_SECRET_KEY_LENGTH);
        Preconditions.checkArgument(recoveryCodes.size() == EXPECTED_RECOVERY_CODE_COUNT);
        clearDataFor(user);
        insertRecoveryCodes(user, recoveryCodes);
        insertSecret(user, secret);
    }

    private void insertSecret(UserID user, byte[] secret)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(DBUtil.insert(T_TWO_FACTOR_SECRET,
                C_TWO_FACTOR_USER_ID, C_TWO_FACTOR_SECRET));
        ps.setString(1, user.getString());
        ps.setBytes(2, secret);
        ps.executeUpdate();
    }

    private void insertRecoveryCodes(UserID user, ImmutableList<String> recoveryCodes)
            throws SQLException
    {
        // Insert the recovery codes and the user secret
        PreparedStatement ps = prepareStatement(DBUtil.insert(T_TWO_FACTOR_RECOVERY,
                C_TF_RECOVERY_USER_ID, C_TF_RECOVERY_CODE, C_TF_RECOVERY_CODE_USE_TS));
        for (String code : recoveryCodes) {
            Preconditions.checkState(code.length() <= RECOVERY_CODE_MAX_LENGTH);
            ps.setString(1, user.getString());
            ps.setString(2, code);
            ps.setDate(3, null);
            Util.verify(ps.executeUpdate() == 1);
        }
    }

    public ImmutableList<RecoveryCode> recoveryCodesFor(UserID user)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_TWO_FACTOR_RECOVERY,
                        C_TF_RECOVERY_USER_ID + "=? ORDER BY " + C_TF_RECOVERY_ID,
                        C_TF_RECOVERY_CODE, C_TF_RECOVERY_CODE_USE_TS));
        ps.setString(1, user.getString());
        Builder<RecoveryCode> builder = ImmutableList.builder();
        ResultSet rs = ps.executeQuery();
        try {
            while (rs.next()) {
                RecoveryCode r = new RecoveryCode(rs.getString(1), rs.getTimestamp(2));
                builder.add(r);
            }
        } finally {
            rs.close();
        }
        ImmutableList<RecoveryCode> result = builder.build();
        Preconditions.checkState(result.size() == EXPECTED_RECOVERY_CODE_COUNT);
        return result;
    }

    public void markRecoveryCodeUsed(UserID user, String code)
            throws SQLException
    {
        Preconditions.checkState(code.length() <= RECOVERY_CODE_MAX_LENGTH);
        PreparedStatement ps = prepareStatement(DBUtil.updateWhere(T_TWO_FACTOR_RECOVERY,
                C_TF_RECOVERY_USER_ID + "=? AND " + C_TF_RECOVERY_CODE + "=?",
                C_TF_RECOVERY_CODE_USE_TS));
        java.util.Date now = new java.util.Date();
        java.sql.Timestamp timestamp = new java.sql.Timestamp(now.getTime());
        ps.setTimestamp(1, timestamp);

        ps.setString(2, user.getString());
        ps.setString(3, code);

        Util.verify(ps.executeUpdate() == 1);
    }

    public byte[] secretFor(UserID user)
            throws SQLException, ExNotFound
    {
        PreparedStatement ps = prepareStatement(DBUtil.selectWhere(T_TWO_FACTOR_SECRET,
                C_TWO_FACTOR_USER_ID + "=?",
                C_TWO_FACTOR_SECRET));
        ps.setString(1, user.getString());
        ResultSet rs = ps.executeQuery();
        try {
            if (!rs.next()) {
                throw new ExNotFound("no second factor for " + user.getString());
            }
            byte[] result = rs.getBytes(1);
            return result;
        } finally {
            rs.close();
        }
    }

    public void clearDataFor(UserID user)
            throws SQLException
    {
        clearRecoveryCodesFor(user);
        clearSecretFor(user);
    }

    private void clearRecoveryCodesFor(UserID user)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(DBUtil.deleteWhere(T_TWO_FACTOR_RECOVERY,
                C_TF_RECOVERY_USER_ID + "=?"));
        ps.setString(1, user.getString());
        ps.executeUpdate();
    }

    private void clearSecretFor(UserID user)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(DBUtil.deleteWhere(T_TWO_FACTOR_SECRET,
                C_TWO_FACTOR_USER_ID + "=?"));
        ps.setString(1, user.getString());
        ps.executeUpdate();
    }
}
