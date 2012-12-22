/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.AbstractSQLDatabase;
import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import com.aerofs.sp.common.Base62CodeGenerator;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.sp.common.SubscriptionParams;
import com.google.common.collect.Sets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.Set;

import static com.aerofs.lib.db.DBUtil.selectWhere;
import static com.aerofs.sp.server.lib.SPSchema.C_ES_EMAIL;
import static com.aerofs.sp.server.lib.SPSchema.C_ES_LAST_EMAILED;
import static com.aerofs.sp.server.lib.SPSchema.C_ES_SUBSCRIPTION;
import static com.aerofs.sp.server.lib.SPSchema.C_ES_TOKEN_ID;
import static com.aerofs.sp.server.lib.SPSchema.C_TI_TIC;
import static com.aerofs.sp.server.lib.SPSchema.C_TI_TO;
import static com.aerofs.sp.server.lib.SPSchema.C_TI_TS;
import static com.aerofs.sp.server.lib.SPSchema.C_USER_ID;
import static com.aerofs.sp.server.lib.SPSchema.T_ES;
import static com.aerofs.sp.server.lib.SPSchema.T_TI;
import static com.aerofs.sp.server.lib.SPSchema.T_USER;

/**
 * N.B. only User.java may refer to this class
 */
public class EmailSubscriptionDatabase extends AbstractSQLDatabase
{
    public EmailSubscriptionDatabase(IDatabaseConnectionProvider<Connection> provider)
    {
        super(provider);
    }

    /**
     * Subscribe a user to a particular category
     */
    public void addEmailSubscription(UserID userId, SubscriptionCategory sc)
            throws SQLException
    {
        addEmailSubscription(userId, sc, System.currentTimeMillis());
    }

    // For testing only
    // TODO (WW) use DI instead
    public void addEmailSubscription(UserID userId, SubscriptionCategory sc, long currentTime)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.insertOnDuplicateUpdate(T_ES, C_ES_LAST_EMAILED + "=?", C_ES_EMAIL,
                        C_ES_TOKEN_ID, C_ES_SUBSCRIPTION, C_ES_LAST_EMAILED));

        Timestamp ts = new Timestamp(currentTime);

        String token = Base62CodeGenerator.newRandomBase62String(
                SubscriptionParams.TOKEN_ID_LENGTH);

        ps.setString(1, userId.toString());
        ps.setString(2, token);
        ps.setInt(3, sc.getCategoryID());
        ps.setTimestamp(4, ts, UTC_CALANDER);
        ps.setTimestamp(5, ts, UTC_CALANDER);

        int result = ps.executeUpdate();

        /*
         * The "INSERT ... ON DUPLICATE KEY UPDATE" function returns 1 for every succesful INSERT
         * and 2 for every succesful UPDATE. That means that if you do the command on 5 rows,
         * 3 of which result in INSERT, and 2 of which result in UPDATE, the return value
         * will be 7 (3*1 + 2*2). In our case, we expect either a single UPDATE, or a single
         * INSERT, so a return value of 1 or 2 is acceptable.
         *
         * See http://bugs.mysql.com/bug.php?id=2709 for more information
         */
        Util.verify(result == 1 || result == 2);
    }

    /**
     * Get the email subscriptions a particular email is associated with
     * @return the subscriptions associated with this email address
     */
    public Set<SubscriptionCategory> getEmailSubscriptions(String email)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.selectWhere(T_ES, C_ES_EMAIL + "=?",
                        C_ES_SUBSCRIPTION));

        ps.setString(1, email);

        ResultSet rs = ps.executeQuery();
        EnumSet<SubscriptionCategory> subscriptions = EnumSet.noneOf(SubscriptionCategory.class);

        try {
            while (rs.next()) {
                int result = rs.getInt(1);
                SubscriptionCategory sc = SubscriptionCategory.getCategoryByID(result);
                subscriptions.add(sc);
            }
            return subscriptions;
        } finally {
            rs.close();
        }
    }

    /**
     * unsubscribe a user from a category
     */
    public void removeEmailSubscription(UserID userId, SubscriptionCategory sc)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.deleteWhereEquals(T_ES, C_ES_EMAIL,
                        C_ES_SUBSCRIPTION));

        ps.setString(1, userId.toString());
        ps.setInt(2, sc.getCategoryID());

        Util.verify(ps.executeUpdate() == 1);
    }

    /**
     * unsubscribe a user from a category based on their token id
     */
    public void removeEmailSubscription(final String tokenId) throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.deleteWhereEquals(T_ES, C_ES_TOKEN_ID));

        ps.setString(1, tokenId);
        Util.verify(ps.executeUpdate() == 1);
    }

    /**
     * get the unsubscribtion token id associated with email and category. This is a unique
     * id associated with an email and a subscription category, used to handle unsubscribe requests
     * from a particular category
     */
    public String getTokenId(final UserID userId, final SubscriptionCategory sc) throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.selectWhere(T_ES,
                        C_ES_EMAIL + "=? and " + C_ES_SUBSCRIPTION + "=?",
                        C_ES_TOKEN_ID));

        ps.setString(1, userId.toString());
        ps.setInt(2, sc.getCategoryID());

        ResultSet rs = ps.executeQuery();
        try {
            if (rs.next()) return rs.getString(1);
            else return null;
        } finally {
            rs.close();
        }
    }

    /**
     * get the email associated with the subscription token id
     */
    public String getEmail(final String tokenId)
            throws SQLException, ExNotFound {
        PreparedStatement ps = prepareStatement(
                DBUtil.selectWhere(T_ES, C_ES_TOKEN_ID + "=?",
                        C_ES_EMAIL)
        );

        ps.setString(1, tokenId);
        ResultSet rs = ps.executeQuery();
        try {
            if (rs.next()) return rs.getString(1);
            else throw new ExNotFound();
        } finally {
            rs.close();
        }
    }

    /**
     * check if a user is subscribed to a category
     * @return true if the subscription category matches a subscription for the user,
     *         false otherwise
     */
    public boolean isSubscribed(UserID userId, SubscriptionCategory sc)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.selectWhere(T_ES,
                        C_ES_EMAIL + "=? and " + C_ES_SUBSCRIPTION + "=?",
                        C_ES_EMAIL)
        );

        ps.setString(1, userId.toString());
        ps.setInt(2, sc.getCategoryID());

        ResultSet rs = ps.executeQuery();
        try {
            return rs.next(); //true if an entry was found, false otherwise
        } finally {
            rs.close();
        }
    }

    public synchronized void setLastEmailTime(UserID userId, SubscriptionCategory category,
            long lastEmailTime)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.updateWhere(T_ES,
                        C_ES_EMAIL + "=? and " + C_ES_SUBSCRIPTION + "=?",
                        C_ES_LAST_EMAILED));

        ps.setTimestamp(1, new Timestamp(lastEmailTime), UTC_CALANDER);
        ps.setString(2, userId.toString());
        ps.setInt(3, category.getCategoryID());
        Util.verify(ps.executeUpdate() == 1);
    }

    /**
     * Get (maxUsers) who have not signed up after (days) days offset by offset rows
     */
    public Set<UserID> getUsersNotSignedUpAfterXDays(final int days, final int maxUsers,
            final int offset)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                "select " + C_TI_TO +
                        " from " + T_TI +
                        " left join " + T_USER + " on " + C_USER_ID + "=" +
                        C_TI_TO +
                        " where " + C_USER_ID + " is null " +
                        " and DATEDIFF(CURRENT_DATE(),DATE(" + C_TI_TS +")) =?" +
                        " limit ? offset ?");

        ps.setInt(1, days);
        ps.setInt(2, maxUsers);
        ps.setInt(3, offset);

        ResultSet rs = ps.executeQuery();
        try {
            Set<UserID> users = Sets.newHashSetWithExpectedSize(maxUsers);
            while (rs.next()) { users.add(UserID.fromInternal(rs.getString(1))); }
            return users;
        } finally {
            rs.close();
        }
    }

    public synchronized int getHoursSinceLastEmail(final UserID userId,
            final SubscriptionCategory category)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(
                DBUtil.selectWhere(T_ES, C_ES_EMAIL + "=? and " +
                        C_ES_SUBSCRIPTION + "=?",
                        "HOUR(TIMEDIFF(CURRENT_TIMESTAMP()," + C_ES_LAST_EMAILED +
                                "))"));

        ps.setString(1, userId.toString());
        ps.setInt(2, category.getCategoryID());

        ResultSet rs = ps.executeQuery();
        try {
            if (rs.next()) return rs.getInt(1);
            else return -1;
        } finally {
            rs.close();
        }
    }

    /**
     * Used to retrieve a folder invitation code to use in the email reminders
     * We don't care which code it is, so long as it can be used to sign up
     */
    public String getOnePendingFolderInvitationCode(UserID to)
            throws SQLException
    {
        PreparedStatement ps = prepareStatement(selectWhere(T_TI, C_TI_TO + "=?", C_TI_TIC));

        ps.setString(1, to.toString());
        ResultSet rs = ps.executeQuery();
        try {
            if (rs.next()) return rs.getString(1);
            else return null;
        } finally {
            rs.close();
        }
    }
}
