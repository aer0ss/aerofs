/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

public interface ISnapshotableNotificationEmitter
{
    // sends a ritual notification of the latest state when a snapshot is requested.
    void sendSnapshot_();
}
