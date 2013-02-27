package com.aerofs.daemon.core.notification;

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
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Inject;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;

public class RitualNotificationServer implements IConnectionManager
{
    private static final Logger l = Util.l(RitualNotificationServer.class);

    private static final Prio PRIO = Prio.HI;

    private TCPProactorMT _proactor;

    // Access is protected by synchronized (_map)
    private final BiMap<InetSocketAddress, ReactorConnector> _map = HashBiMap.create();

    private final CoreQueue _cq;
    private final CoreScheduler _sched;
    private final DownloadState _dls;
    private final UploadState _uls;
    private final PathStatusNotifier _psn;
    private final PathStatus _so;
    private final SyncStatusSynchronizer _sss;
    private final AggregateSyncStatus _agss;
    private final DirectoryService _ds;
    private final TC _tc;
    private final ServerConnectionStatus _scs;
    private final ConflictState _cl;

    @Inject
    public RitualNotificationServer(CoreQueue cq, CoreScheduler sched, TC tc, DirectoryService ds,
            DownloadState dls, UploadState uls, PathStatus so, ServerConnectionStatus scs,
            SyncStatusSynchronizer sss, AggregateSyncStatus agss, ConflictState cl)
    {
        _cq = cq;
        _sched = sched;
        _dls = dls;
        _uls = uls;
        _so = so;
        _sss = sss;
        _agss = agss;
        _ds = ds;
        _tc = tc;
        _scs = scs;
        _cl = cl;
        _psn = new PathStatusNotifier(this, _ds, _so);
    }

    public void init_() throws IOException
    {
        _dls.addListener_(new DownloadStateListener(this, _ds, _tc));
        _uls.addListener_(new UploadStateListener(this, _ds, _tc));

        // Merged status notifier listens on all input sources
        final PathStatusNotifier sn = new PathStatusNotifier(this, _ds, _so);
        _dls.addListener_(sn);
        _uls.addListener_(sn);
        _agss.addListener_(sn);
        _cl.addListener_(sn);

        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                try {
                    _cl.sendSnapshot_(sn);
                } catch (SQLException e) {
                    l.warn("Failed to send conflict snapshot", e);
                }
            }
        }, 0);

        // detect apparition of new device in a store to send CLEAR_CACHE message
        _sss.addListener_(new IListener() {
            @Override
            public void devicesChanged(Set<SIndex> stores)
            {
                clearStatusCache_();
            }
        });

        // detect change of server availability to send CLEAR_CACHE message
        _scs.addListener(new IServiceStatusListener() {
            @Override
            public boolean isAvailable(ImmutableMap<Server, Boolean> status)
            {
                return status.get(Server.VERKEHR) && status.get(Server.SYNCSTAT);
            }

            @Override
            public void available() {
                l.info("sss available");
                // sync status back up: must clear cache in shellext to avoid false negatives
                // this callback is never called with the core lock held, must schedule...
                scheduleClearStatusCache();
            }

            @Override
            public void unavailable()
            {
                l.info("sss unavailable");
                // sync status down: must clear cache in shellext to avoid showing outdated status
                // this callback is never called with the core lock held, must schedule...
                scheduleClearStatusCache();
            }
        }, Server.VERKEHR, Server.SYNCSTAT);

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

        // must enqueue since this method is called by non-core threads
        _cq.enqueueBlocking(new EISendSnapshot(_tc, this, from, _dls, _uls, _psn, _ds), PRIO);

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
        // only core threads can access cr._snapshotSent
        assert _tc.isCoreThread();

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

    private void scheduleClearStatusCache()
    {
        // this is kinda retarted but due to the snapshot logic notifications can only be sent from
        // a core thread, even though other synchronization is used
        // TODO: investigate a refactoring that removes this requirement
        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                clearStatusCache_();
            }
        }, 0);
    }

    private void clearStatusCache_()
    {
        sendEvent_(PBNotification.newBuilder()
                .setType(Type.CLEAR_STATUS)
                .build());
    }

    public void sendEvent_(PBNotification pb)
    {
        // only core threads can access cr._snapshotSent
        assert _tc.isCoreThread();

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
