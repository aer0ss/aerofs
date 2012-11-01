/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.syncstat;

import java.net.URL;

public class SyncStatusClientFactory
{
    public static SyncStatusClient newClient(URL url, String user)
    {
        return new SyncStatusClient(new SyncStatusClientHandler(url), user);
    }

    public static SyncStatusBlockingClient newBlockingClient(URL url, String user)
    {
        return new SyncStatusBlockingClient(new SyncStatusClientHandler(url), user);
    }
}
