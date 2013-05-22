/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual_notification;

import com.aerofs.proto.RitualNotifications.PBNotification;

public interface IRitualNotificationListener
{
    /**
     * N.B. methods of this interface are called in an independent notification thread
     */
    void onNotificationReceived(PBNotification notification);

    void onNotificationChannelBroken();
}
