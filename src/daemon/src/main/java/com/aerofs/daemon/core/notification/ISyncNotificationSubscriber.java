package com.aerofs.daemon.core.notification;

import com.aerofs.daemon.core.store.Store;
import com.aerofs.ssmp.EventHandler;
import com.aerofs.ssmp.SSMPClient.ConnectionListener;
import com.aerofs.ssmp.SSMPEvent;

public interface ISyncNotificationSubscriber extends ConnectionListener, EventHandler
{
    default void init_() {};

    default void subscribe_(Store s) {};

    default void unsubscribe_(Store s) {}

    @Override default void eventReceived(SSMPEvent e) {}

    @Override default void connected() {}

    @Override default void disconnected() {};
}
