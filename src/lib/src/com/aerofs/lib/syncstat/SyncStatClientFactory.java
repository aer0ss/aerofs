package com.aerofs.lib.syncstat;

import java.net.URL;

public class SyncStatClientFactory {
    public static SyncStatClient newClient(URL url, String user)
    {
        return new SyncStatClient(new SyncStatClientHandler(url), user);
    }

    public static SyncStatBlockingClient newBlockingClient(URL url, String user)
    {
        return new SyncStatBlockingClient(new SyncStatClientHandler(url), user);
    }
}
