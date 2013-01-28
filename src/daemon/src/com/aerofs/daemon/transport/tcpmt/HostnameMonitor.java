package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.transport.tcpmt.ARP.ARPChange;
import com.aerofs.daemon.transport.tcpmt.ARP.IARPChangeListener;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Map;

// hostname here can be either hostname or textual ip address

class HostnameMonitor implements IARPChangeListener
{
    private static final Logger l = Util.l(HostnameMonitor.class);

    private static class Host
    {
        final String _hostname;
        final int _port;

        boolean _online;
        boolean _errorLogged;

        Host(String hostname, int port)
        {
            _hostname = hostname;
            _port = port;
        }
    }

    private final TCP t;
    private final Map<DID, Host> _monitoredHosts = Maps.newTreeMap();

    private boolean _notified;
    private long _lastRound;

    public HostnameMonitor(TCP t, ARP arp)
    {
        this.t = t;

        arp.addARPChangeListener(this); // FIXME (AG): not safe to leak 'this' during construction
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
        ArrayList<Map.Entry<DID, Host>> list = Lists.newArrayList();

        while (true) {
            synchronized (this) {
                if (!_notified) {
                    ThreadUtil.waitUninterruptable(this, DaemonParam.TCP.RETRY_INTERVAL);
                }
                _notified = false;

                list.clear();

                // make a copy so that we won't hold the lock for too long
                for (Map.Entry<DID, Host> en : _monitoredHosts.entrySet()) {
                    if (!en.getValue()._online) list.add(en);
                }
            }

            // remaining time to wait to satisfy the HOSTNAME_MONITOR_MIN_DELAY
            long delay = DaemonParam.HOSTNAME_MONITOR_MIN_DELAY + _lastRound -
                System.currentTimeMillis();

            if (delay > 0) ThreadUtil.sleepUninterruptable(delay);

            _lastRound = System.currentTimeMillis();

            for (Map.Entry<DID, Host> en : list) {
                String host = en.getValue()._hostname;
                try {
                    // resolve
                    InetAddress addr = InetAddress.getByName(host);
                    InetSocketAddress ep = new InetSocketAddress(addr,
                            en.getValue()._port);

                    // try send something
                    PBTPHeader h = PBTPHeader
                            .newBuilder()
                            .setType(Type.TCP_NOP)
                            .build();
                    t.ucast().sendControl(en.getKey(), ep, h, Prio.LO);

                    // arp entry is added when we receive TCP_STORES
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
        Host en = _monitoredHosts.get(did);
        if (en == null) return;

        en._online = true;
        en._errorLogged = false;
    }

    public synchronized void offline(DID did)
    {
        Host en = _monitoredHosts.get(did);
        if (en == null) return;

        en._online = false;

        _notified = true;
        notify();
    }

    public synchronized void put(DID did, String hostname, int port)
    {
        Host en = new Host(hostname, port);
        _monitoredHosts.put(did, en);

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
        return (_monitoredHosts.get(did) != null);
    }

    public synchronized void remove(DID did)
    {
        _monitoredHosts.remove(did);
    }

    //
    // IARPChangeListener methods
    //

    @Override
    public void onArpChange_(DID did, ARPChange chg)
    {
        if (chg == ARPChange.ADD) {
            online(did);
        }
    }
}
