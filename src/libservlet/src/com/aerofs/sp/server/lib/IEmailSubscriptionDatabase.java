package com.aerofs.sp.server.lib;

import java.util.Set;

import com.aerofs.lib.spsv.sendgrid.SubscriptionCategory;

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
     */
    void addEmailSubscription(String email, SubscriptionCategory sc) throws SQLException;

    /**
     * subscribe a user to a few categories
     */
    void subscribeToCategories(String email, Set<SubscriptionCategory> s) throws SQLException;

    /**
     * unsubscribe a user from a category
     */
    void removeEmailSubscription(String email, SubscriptionCategory sc) throws SQLException;

    /**
     * check if a user is subscribed to a category
     * @return true if the subscription category matches a subscription for the user,
     *         false otherwise
     */
    boolean isSubscribed(String email, SubscriptionCategory sc) throws SQLException;

    /**
     * add a row to the database indicating a user needs to be reminded of some event
     */
    void addNewEmailReminder(String email, SubscriptionCategory category, long firstEmailTime)
            throws SQLException;

    void updateEmailReminder(String email, SubscriptionCategory category, long lastEmailTime)
            throws SQLException;
}
