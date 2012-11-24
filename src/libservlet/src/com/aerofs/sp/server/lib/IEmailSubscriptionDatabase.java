package com.aerofs.sp.server.lib;

import java.util.Set;

import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.common.SubscriptionCategory;

import java.sql.SQLException;

public interface IEmailSubscriptionDatabase
{
    /**
     * Get the email subscriptions a particular email is associated with
     * @return the subscriptions associated with this email address
     */
    Set<SubscriptionCategory> getEmailSubscriptions(String emailAddress) throws SQLException;

    /**
     * subscribe a user to a particular category
     *
     * @return the tokenID generated for this [email, sc] tuple
     */
    String addEmailSubscription(UserID userId, SubscriptionCategory sc, long time)
            throws SQLException;
    String addEmailSubscription(UserID userId, SubscriptionCategory sc) throws SQLException;

    /**
     * unsubscribe a user from a category
     */
    void removeEmailSubscription(UserID ueerId, SubscriptionCategory sc) throws SQLException;

    /**
     * unsubscribe a user from a category based on their token id
     */
    void removeEmailSubscription(String tokenId) throws SQLException;

    /**
     * get the unsubscribtion token id associated with email and category. This is a unique
     * id associated with an email and a subscription category, used to handle unsubscribe requests
     * from a particular category
     */
    String getTokenId(UserID userId, SubscriptionCategory sc) throws SQLException;


    /**
     * Used to retrieve a folder invitation code to use in the email reminders
     * We don't care which code it is, so long as it can be used to sign up
     */

    String getOnePendingFolderInvitationCode(UserID to) throws SQLException;
    /**
     * get the email associated with the subscription token id
     */
    String getEmail(String tokenId)
            throws SQLException, ExNotFound;
    /**
     * check if a user is subscribed to a category
     * @return true if the subscription category matches a subscription for the user,
     *         false otherwise
     */
    boolean isSubscribed(UserID userId, SubscriptionCategory sc) throws SQLException;

    void setLastEmailTime(UserID userId, SubscriptionCategory category,
            long lastEmailTime) throws SQLException;

    /**
     * Get (maxUsers) who have not signed up after (days) days offset by offset rows
     */
    Set<UserID> getUsersNotSignedUpAfterXDays(int days, int maxUsers, int offset)
            throws SQLException;


    int getHoursSinceLastEmail(UserID userId, SubscriptionCategory category)
            throws SQLException;
}
