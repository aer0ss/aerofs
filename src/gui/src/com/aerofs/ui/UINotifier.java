package com.aerofs.ui;

import com.aerofs.controller.IViewNotifier;
import com.aerofs.lib.notifier.Listeners;
import com.aerofs.proto.ControllerNotifications.Type;
import com.google.protobuf.GeneratedMessageLite;

import javax.annotation.Nullable;

import java.util.EnumMap;

/**
 * This is the Java UI implementation of the controller's IViewNotifier interface
 * It provides methods to allow code in the Java UI to listen for specific notifications
 */
public class UINotifier implements IViewNotifier
{
    private final EnumMap<Type, Listeners<IUINotificationListener>> map =
            new EnumMap<Type, Listeners<IUINotificationListener>>(Type.class);

    /**
     * Add a listener for a specific notification type.
     * This method is thread-safe.
     */
    public void addListener(Type type, IUINotificationListener listener)
    {
        synchronized (map) {
            Listeners<IUINotificationListener> ls = map.get(type);
            if (ls == null) {
                ls = Listeners.newListeners();
                map.put(type, ls);
            }
            ls.addListener_(listener);
        }
    }

    /**
     * Remove a listener for a specific notification type
     * No-op if the listener wasn't found
     * This method is thread-safe
     */
    public void removeListener(Type type, IUINotificationListener listener)
    {
        synchronized (map) {
            map.get(type).removeListener_(listener);
        }
    }

    @Override
    public void notify(final Type type, final @Nullable GeneratedMessageLite notification)
    {
        if (UI.get() != null) {
            // We're running a Java UI. Notify in the context of the UI thread
            UI.get().asyncExec(new Runnable()
            {
                @Override
                public void run()
                {
                    doNotify(type, notification);
                }
            });
        } else {
            // We're running a native UI. Notify in the context of the caller thread
            // TODO: what would be the most appropriate thread to deliver the notification on?
            doNotify(type, notification);
        }
    }

    private void doNotify(Type type, GeneratedMessageLite notification)
    {
        synchronized (map) {
            Listeners<IUINotificationListener> ls = map.get(type);
            if (ls != null) {
                try {
                    for (IUINotificationListener l : ls.beginIterating_()) {
                        l.onNotificationReceived(notification);
                    }
                } finally {
                    ls.endIterating_();
                }
            }
        }
    }
}
