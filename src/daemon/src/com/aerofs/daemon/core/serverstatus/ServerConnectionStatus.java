/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.serverstatus;

import com.aerofs.daemon.core.serverstatus.IConnectionStatusNotifier.IListener;
import com.aerofs.daemon.core.syncstatus.SyncStatusConnection;
import com.aerofs.daemon.core.verkehr.VerkehrNotificationSubscriber;
import com.aerofs.lib.Util;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.util.Map;

/**
 * This class keeps tracks of connection status to the different servers the daemon is talking to
 *
 * The main purpose is to centralize the knowledge of which server are up and which are experiencing
 * problems so we can warn the users that some features are temporarily not working
 */
public class ServerConnectionStatus
{
    public static enum Server
    {
        VERKEHR, SYNCSTAT
    }

    private static final Logger l = Util.l(ServerConnectionStatus.class);
    private final Map<Server, Boolean> _status = Maps.newEnumMap(Server.class);

    @Inject
    ServerConnectionStatus(VerkehrNotificationSubscriber vks, SyncStatusConnection ssc)
    {
        addListener_(vks, Server.VERKEHR);
        addListener_(ssc, Server.SYNCSTAT);
    }

    /**
     * Helper method to add a listener to a connection
     */
    private void addListener_(IConnectionStatusNotifier csn, final Server server)
    {
        csn.addListener_(new IListener()
        {
            @Override
            public void onConnected()
            {
                synchronized (ServerConnectionStatus.this) {
                    l.info("connected " + server);
                    _status.put(server, true);
                }
            }

            @Override
            public void onDisconnected()
            {
                synchronized (ServerConnectionStatus.this) {
                    l.info("disconnected " + server);
                    _status.put(server, false);
                }
            }
        });
    }

    /**
     * @return connection status of a given server
     */
    public synchronized boolean isConnected(Server server)
    {
        Boolean r = _status.get(server);
        return r != null ? r : false;
    }

    /**
     * @return connection status of a list of servers AND'ed together
     */
    public synchronized boolean isConnected(Server... servers)
    {
        boolean r = true;
        for (Server server : servers) {
            r &= isConnected(server);
        }
        return r;
    }

    /**
     * @return connection status of all servers AND'ed together
     */
    public synchronized boolean isConnected()
    {
        boolean r = true;
        for (Server server : Server.values()) {
            r &= isConnected(server);
        }
        return r;
    }
}
