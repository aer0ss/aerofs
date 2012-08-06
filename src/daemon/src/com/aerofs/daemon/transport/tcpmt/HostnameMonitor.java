package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.DID;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

// hostname here can be either hostname or textual ip address

class HostnameMonitor {
    private static final Logger l = Util.l(HostnameMonitor.class);

    private static class Entry {
        final String _hostname;
        final int _port;
        boolean _online;
        boolean _errorLogged;

        Entry(String hostname, int port)
        {
            _hostname = hostname;
            _port = port;
        }
    }

    private final TCP t;
    private final Map<DID, Entry> _map = new TreeMap<DID, Entry>();
    private boolean _notified;
    private long _lastRound;

    public HostnameMonitor(TCP t)
    {
        this.t = t;
    }

    public void start()
    {
        new Thread(new Runnable() {
            @Override
            public void run()
            {
                thdMonitor();
            }
        }, "tcpmt.hostname-monitor").start();
    }

    private void thdMonitor()
    {
        ArrayList<Map.Entry<DID, Entry>> list =
            new ArrayList<Map.Entry<DID, Entry>>();

        while (true) {
            synchronized (this) {
                if (!_notified) {
                    Util.waitUninterruptable(this, DaemonParam.TCP.RETRY_INTERVAL);
                }
                _notified = false;

                list.clear();

                // make a copy so that we won't hold the lock for too long
                for (Map.Entry<DID, Entry> en : _map.entrySet()) {
                    if (!en.getValue()._online) list.add(en);
                }
            }

            // remaining time to wait to satisfy the HOSTNAME_MONITOR_MIN_DELAY
            long delay = DaemonParam.HOSTNAME_MONITOR_MIN_DELAY + _lastRound -
                System.currentTimeMillis();

            if (delay > 0) Util.sleepUninterruptable(delay);

            _lastRound = System.currentTimeMillis();

            for (Map.Entry<DID, Entry> en : list) {
                String host = en.getValue()._hostname;
                try {
                    // resolve
                    InetAddress addr = InetAddress.getByName(host);
                    InetSocketAddress ep = new InetSocketAddress(addr,
                            en.getValue()._port);

                    // try send something
                    PBTPHeader h = PBTPHeader.newBuilder()
                        .setType(Type.TCP_NOP)
                        .build();
                    t.ucast().sendControl(en.getKey(), ep, h, Prio.LO);

                    // arp entry will be added when we receive TCP_STORES
                    //t.arp().put(en.getKey(), ep);

                } catch (Exception e) {
                    if (!en.getValue()._errorLogged) {
                        en.getValue()._errorLogged = true;
                        l.info(host + ':' + en.getValue()._port + ": " +
                                e.getClass().getSimpleName()
                                + ". retry later (reported only once)");
                    }
                }
            }
        }
    }

    public synchronized void online(DID did)
    {
        Entry en = _map.get(did);
        if (en == null) return;

        en._online = true;
        en._errorLogged = false;
    }

    public synchronized void offline(DID did)
    {
        Entry en = _map.get(did);
        if (en == null) return;

        en._online = false;

        _notified = true;
        notify();
    }

    public synchronized void put(DID did, String hostname, int port)
    {
        Entry en = new Entry(hostname, port);
        _map.put(did, en);

        // don't delay it
        _lastRound = 0;
        _notified = true;
        notify();
    }

    /**
     * Checks if this remote peer has a static ip and is being watched by the
     * <code>HostnameMonitor</code>
     *
     * @param did {@link DID} of the remote peer to check
     * @return true if this device has a static ip and is being watched by the
     * <code>HostnameMonitor</code>; false otherwise
     */
    public synchronized boolean has(DID did)
    {
        return (_map.get(did) != null);
    }

    public synchronized void remove(DID did)
    {
        _map.remove(did);
    }
}
