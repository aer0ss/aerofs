package com.aerofs.polaris.notification;

import com.aerofs.ids.UniqueID;

public interface Notifier {

    void notifyStoreUpdated(UniqueID store, Long updateTimestamp);
}
