package com.aerofs.ui;

import com.google.protobuf.GeneratedMessageLite;

public interface IUINotificationListener
{
    void onNotificationReceived(GeneratedMessageLite notification);
}
