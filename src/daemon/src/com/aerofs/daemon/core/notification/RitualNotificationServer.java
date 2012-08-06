package com.aerofs.daemon.core.notification;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.DownloadState;
import com.aerofs.daemon.core.net.UploadState;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.transport.lib.TCPProactorMT;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IConnection;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IConnectionManager;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IConnector;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IReactor;
import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.apache.log4j.Logger;

public class RitualNotificationServer implements IConnectionManager
{
    private static final Logger l = Util.l(RitualNotificationServer.class);

    private static final Prio PRIO = Prio.HI;

    private TCPProactorMT _proactor;

    // Access is protected by synchronized (_map)
    private final BiMap<InetSocketAddress, ReactorConnector> _map = HashBiMap.create();

    private final CoreQueue _cq;
    private final DownloadState _dls;
    private final UploadState _uls;
    private final DirectoryService _ds;
    private final TC _tc;

    @Inject
    public RitualNotificationServer(CoreQueue cq, DownloadState dls, UploadState uls,
            DirectoryService ds, TC tc)
    {
        _cq = cq;
        _dls = dls;
        _uls = uls;
        _ds = ds;
        _tc = tc;
    }

    public void init_() throws IOException
    {
        _dls.addListener_(new DownloadStateListener(this, _ds, _tc));
        _uls.addListener_(new UploadStateListener(this, _ds, _tc));
        SPBlockingClient.setListener(new DaemonBadCredentialListener(this));

        _proactor = new TCPProactorMT("notifier", this,
                C.LOCALHOST_ADDR, Cfg.port(Cfg.PortType.RITUAL_NOTIFICATION),
                C.RITUAL_NOTIFICATION_MAGIC, true, Integer.MAX_VALUE);
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
        _cq.enqueueBlocking(new EISendSnapshot(_tc, this, from, _dls, _uls, _ds), PRIO);

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

    void sendEvent_(PBNotification pb)
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
            Util.l(this).warn(Util.e(e));
            _proactor.disconnect(to);
        }
    }
}
