package com.aerofs.daemon.core.notification;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.protocol.DownloadState;
import com.aerofs.daemon.core.net.UploadState;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.IServiceStatusListener;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.Server;
import com.aerofs.daemon.core.status.PathStatus;
import com.aerofs.daemon.core.syncstatus.AggregateSyncStatus;
import com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer;
import com.aerofs.daemon.core.syncstatus.SyncStatusSynchronizer.IListener;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.transport.lib.TCPProactorMT;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IConnection;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IConnectionManager;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IConnector;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IReactor;
import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.SIndex;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Inject;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

public class RitualNotificationServer implements IConnectionManager
{
    private static final Logger l = Loggers.getLogger(RitualNotificationServer.class);

    private static final Prio PRIO = Prio.HI;

    private TCPProactorMT _proactor;

    // Access is protected by synchronized (_map)
    private final BiMap<InetSocketAddress, ReactorConnector> _map = HashBiMap.create();

    interface IConnectionListener
    {
        void onIncomingconnection(InetSocketAddress from);
    }

    private final List<IConnectionListener> _listeners = Lists.newArrayList();

    @Inject
    public RitualNotificationServer()
    {
    }

    void addListener_(IConnectionListener listener)
    {
        _listeners.add(listener);
    }

    public void init_() throws IOException
    {
        SPBlockingClient.setListener(new DaemonBadCredentialListener(this));

        _proactor = new TCPProactorMT("notifier", this,
                Param.LOCALHOST_ADDR, Cfg.port(Cfg.PortType.RITUAL_NOTIFICATION),
                Param.RITUAL_NOTIFICATION_MAGIC, true, Integer.MAX_VALUE);
    }

    public void start_()
    {
        _proactor.start_();
    }

    @Override
    public IReactor newIncomingConnection(IConnection c, InetSocketAddress from)
            throws IOException
    {
        ReactorConnector rc;

        synchronized (_map) {
            if (_map.containsKey(from)) {
                throw new IOException("incoming connection from " + from + " already exist");
            }

            rc = new ReactorConnector(this);
            _map.put(from, rc);
        }

        _proactor.reuseForOutgoingConnection(from, c);

        for (IConnectionListener listner : _listeners) listner.onIncomingconnection(from);

        return rc;
    }

    @Override
    public IConnector newOutgoingConnection(IConnection c, InetSocketAddress to, Object cookie)
            throws IOException
    {
        ReactorConnector rc;
        synchronized (_map) { rc = _map.get(to); }

        if (rc == null) {
            throw new IOException("incoming connection doesn't exist. refuse sending");
        }

        return rc;
    }

    void disconnected(ReactorConnector rc)
    {
        synchronized (_map) { _map.inverse().remove(rc); }
    }

    void sendSnapshot_(InetSocketAddress to, PBNotification ... pbs)
    {
        ReactorConnector cr;
        synchronized (_map) { cr = _map.get(to); }

        if (cr == null) {
            l.warn("connection doesn't exist when sending snapshot");
        } else if (cr._snapshotSent) {
            // this may happen if the client connects, disconnects and reconnects before the
            // EISendSnapshot caused by the first connection has processed by the core.
            l.warn("duplicate snapshots. kill connection");
            _proactor.disconnect(to);
        } else {
            sendImpl(to, pbs);
            cr._snapshotSent = true;
        }
    }

    void clearStatusCache_()
    {
        sendEvent_(PBNotification.newBuilder()
                .setType(Type.CLEAR_STATUS)
                .build());
    }

    public void rootsChanged_()
    {
        sendEvent_(PBNotification.newBuilder()
                .setType(Type.ROOTS_CHANGED)
                .build());
    }

    public void sendEvent_(PBNotification pb)
    {
        synchronized (_map) {
            for (Entry<InetSocketAddress, ReactorConnector> en : _map.entrySet()) {
                if (!en.getValue()._snapshotSent) {
                    l.warn("event before snapshot. ignored " + en.getKey());
                } else {
                    sendImpl(en.getKey(), pb);
                }
            }
        }
    }

    /**
     * This method kills the connection and returns silently if sending fails.
     *
     * TODO when this method is rewritten using netty, it should not block any more and must kill
     * the connection if non-blocking write is not possible.
     */
    private void sendImpl(InetSocketAddress to, PBNotification ... pbs)
    {
        try {
            for (PBNotification pb : pbs) {
                byte bss[][] = new byte[][] { pb.toByteArray() };
                _proactor.send(to, bss, PRIO);
            }
        } catch (Exception e) {
            l.warn(Util.e(e));
            _proactor.disconnect(to);
        }
    }
}
