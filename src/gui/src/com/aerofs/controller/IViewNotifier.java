package com.aerofs.controller;

import com.aerofs.proto.ControllerNotifications.Type;
import com.google.protobuf.GeneratedMessageLite;

import javax.annotation.Nullable;

public interface IViewNotifier
{
    /**
     * This method is called by the controller to deliver a notification to the view.
     * Notifications consist of a protobuf message identified by a type - see notifications.proto
     * for a list of possible notifications and types.
     *
     * This method _must_ be thread-safe.
     *
     * @param type the notification type
     * @param notification instance of a PB message that matches the type
     */
    void notify(Type type, @Nullable GeneratedMessageLite notification);
}
