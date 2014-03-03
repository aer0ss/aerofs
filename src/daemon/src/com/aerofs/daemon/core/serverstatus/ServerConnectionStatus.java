/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.serverstatus;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.serverstatus.IConnectionStatusNotifier.IListener;
import com.aerofs.daemon.core.verkehr.VerkehrNotificationSubscriber;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * This class keeps tracks of connection status to the different servers the daemon is talking to
 *
 * The main purpose is to centralize the knowledge of which server are up and which are experiencing
 * problems so we can warn the users that some features are temporarily not working
 *
 * DEPRECATED: this class doesn't provide much value and isn't necessary; future clients should
 * listen to the services they are interested in directly instead of going through this class.
 */
public class ServerConnectionStatus
{
    public static enum Server
    {
        VERKEHR
    }

    /**
     * A service can depend on a collection of servers and have an arbitrary availability function
     * based on the connection status of each individual server. The callbacks are edge-triggered.
     */
    public static interface IServiceStatusListener
    {
        boolean isAvailable(ImmutableMap<Server, Boolean> statuses);

        void available();
        void unavailable();
    }

    /**
     * A special purpose pair whose second value is mutable
     */
    private static class ServiceStatusEdgeDetector
    {
        private final IServiceStatusListener _service;
        private boolean _level;

        ServiceStatusEdgeDetector(IServiceStatusListener service, boolean level)
        {
            _service = service;
            _level = level;
        }

        void detectEdge(ImmutableMap<Server, Boolean> status)
        {
            boolean newLevel = _service.isAvailable(status);
            if (newLevel != _level) {
                _level = newLevel;
                l.info("service status edge detected");
                if (_level) {
                    _service.available();
                } else {
                    _service.unavailable();
                }
            }
        }
    }

    private static final Logger l = Loggers.getLogger(ServerConnectionStatus.class);
    private final Map<Server, Boolean> _status = Maps.newEnumMap(Server.class);
    private final Map<Server, List<ServiceStatusEdgeDetector>> _listeners
            = Maps.newEnumMap(Server.class);

    @Inject
    ServerConnectionStatus(VerkehrNotificationSubscriber vks)
    {
        addListener_(vks, Server.VERKEHR);
    }

    private synchronized void setStatus(Server server, boolean connected)
    {
        _status.put(server, connected);

        // detect service status transition
        List<ServiceStatusEdgeDetector> l = _listeners.get(server);
        if (l == null) return;

        ImmutableMap<Server, Boolean> status = ImmutableMap.copyOf(_status);
        for (ServiceStatusEdgeDetector ed : l) ed.detectEdge(status);
    }

    /**
     * Helper method to add a listener to a connection
     */
    private void addListener_(IConnectionStatusNotifier csn, final Server server)
    {
        // set default server status
        assert !_status.containsKey(server) : "Multiple status notifiers for " + server;
        _status.put(server, false);

        csn.addListener_(new IListener() {
            @Override
            public void onConnected()
            {
                l.debug("connected " + server);
                setStatus(server, true);
            }

            @Override
            public void onDisconnected()
            {
                l.debug("disconnected " + server);
                setStatus(server, false);
            }
        }, MoreExecutors.sameThreadExecutor());
    }

    /**
     * @return connection status of a given server
     */
    public synchronized boolean isConnected(Server server)
    {
        return _status.get(server);
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

    /**
     * Add a service listener depending on a given list of servers
     *
     * The {@link IServiceStatusListener#available} callback will be called if the service is
     * available at the time of the call.
     */
    public synchronized void addListener(IServiceStatusListener listener, Server... servers)
    {
        boolean level = listener.isAvailable(ImmutableMap.copyOf(_status));
        ServiceStatusEdgeDetector ed = new ServiceStatusEdgeDetector(listener, level);
        for (Server server : servers) {
            List<ServiceStatusEdgeDetector> l = _listeners.get(server);
            if (l == null) {
                l = Lists.newLinkedList();
                _listeners.put(server, l);
            }
            l.add(ed);
        }
        if (level) listener.available();
    }
}
