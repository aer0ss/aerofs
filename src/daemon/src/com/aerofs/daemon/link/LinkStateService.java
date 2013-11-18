/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.link;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.notifier.IListenerVisitor;
import com.aerofs.lib.notifier.Notifier;
import com.aerofs.sv.client.SVClient;
import com.aerofs.swig.driver.Driver;
import com.aerofs.swig.driver.DriverConstants;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.lib.ThreadUtil.startDaemonThread;
import static com.google.common.collect.ImmutableSet.copyOf;

/**
 * This class monitors the state of local NICs
 */
public class LinkStateService
{
    protected static final Logger l = Loggers.getLogger(LinkStateService.class);

    private final AtomicBoolean _started = new AtomicBoolean(false);
    private final Notifier<ILinkStateListener> _notifier = Notifier.create();
    private volatile ImmutableSet<NetworkInterface> _ifaces = ImmutableSet.of();
    private volatile boolean _markedDown; // whether we should act as if all links are down

    // getActiveInterfaces can be slooooooooooooow so we execute while _NOT_ holding
    // the core lock to prevent CoreProgressWatcher from killing the daemon because of
    // it (and to avoid preventing the daemon from making progress during that time...)
    private ImmutableSet<NetworkInterface> getActiveInterfaces()
            throws SocketException
    {
        ImmutableSet.Builder<NetworkInterface> ifaceBuilder = ImmutableSet.builder();

        for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
            NetworkInterface iface = e.nextElement();
            if (isActive(iface)) ifaceBuilder.add(iface);
        }

        return ifaceBuilder.build();
    }

    private boolean isActive(NetworkInterface iface)
            throws SocketException
    {
        // Can't filter by MAC address, since within a virtual machine
        // my eth0 has the vm company's virtual MAC prefix, and on a physical box
        // the vmnet1, vmnet2, etc. has the vm company's virtual MAC prefix.
        //
        // i.e, on hosu ifconfig -a gives the following:
        //
        // eth1     Link encap:Ethernet  HWaddr 00:0c:29:23:d7:c4
        //          inet addr:192.168.2.122  Bcast:192.168.2.255  Mask:255.255.255.0
        //          inet6 addr: fe80::20c:29ff:fe23:d7c4/64 Scope:Link
        //          UP BROADCAST RUNNING MULTICAST  MTU:1500  Metric:1
        //          RX packets:551 errors:550 dropped:0 overruns:0 frame:0
        //          TX packets:80 errors:0 dropped:0 overruns:0 carrier:0
        //          collisions:0 txqueuelen:1000
        //          RX bytes:108107 (105.5 KB)  TX bytes:8650 (8.4 KB)
        //          Interrupt:16 Base address:0x2024
        //
        // notice that the first 3 bytes of the MAC address are 00:0c:29 aka vmware2
        //
        // now, on Roncy ifconfig -a gives:
        //
        // vmnet1: flags=8863<UP,BROADCAST,SMART,RUNNING,SIMPLEX,MULTICAST> mtu 1500
        //         ether 00:50:56:c0:00:01
        //         inet 192.168.235.1 netmask 0xffffff00 broadcast 192.168.235.255
        // vmnet8: flags=8863<UP,BROADCAST,SMART,RUNNING,SIMPLEX,MULTICAST> mtu 1500
        //         ether 00:50:56:c0:00:08
        //         inet 172.16.47.1 netmask 0xffffff00 broadcast 172.16.47.255
        //
        // notice that the first 3 bytes of the MAC addresses are 00:50:56, aka vmware3
        //
        // this means that if I simply filter out virtual adapters I won't be
        // able to run AeroFS on virtual machines. not good.

        // cache interface information as local variables, since some queries on some Windows
        // computers are very slow. Experiments showed that getName and isUp can take 100ms
        // each on computers with VirtualBox installed :S

        // This method takes a very long time on some computers. The following three info loggings
        // are to figure out which system call takes most of the time.
        final String name = iface.getName();
        final boolean isUp = iface.isUp();
        final boolean isLoopback = iface.isLoopback();
        final boolean isVirtual = iface.isVirtual();

        // If debug is enabled, generate the debug message
        StringBuilder sb = new StringBuilder();
        if (l.isDebugEnabled()) {
            sb.append(' ').append(name);

            // The displayName can be different than the name. If they are different,
            // output something like "name[displayName]".
            final String displayName = iface.getDisplayName();
            if (displayName != null && !displayName.equals(name)) {
                sb.append('[').append(displayName).append(']');
            }

            // Output the link state. Inclusion of a symbol means the state it represents
            // is true. i.e, if 'u' is present in the message, then u = true, meaning isUp
            // is true.
            sb.append('(');
            if (isUp) sb.append('u');
            if (isLoopback) sb.append('l');
            if (isVirtual) sb.append('v');
            if (iface.supportsMulticast()) sb.append('m');
            sb.append(')');
        }

        // IMPORTANT: On Mac OS X, disabling an interface via Network Preferences
        // simply removes the interface's IPV4 address. This caused a situation where
        // we would remove IFprev(IPV4, IPV6) and then _readd_ IFnew(IPV6).
        // This caused Multicast::send to attempt to send packets through this
        // interface even though it wasn't 'active'.
        boolean isActive = false;
        if (isUp && !isLoopback && !isVirtual) {
            Enumeration<InetAddress> ias = iface.getInetAddresses();
            while (ias.hasMoreElements()) {
                InetAddress address = ias.nextElement();
                if (address instanceof Inet4Address) {
                    // it is an IPv4 address
                    isActive = true;
                    if (l.isDebugEnabled()) sb.append(':').append(address.getHostAddress());
                    break;
                }
            }
        }

        if (l.isDebugEnabled()) l.debug("ls:ifs:\n" + sb);

        return isActive;
    }

    private void checkLinkState()
    {
        try {
            l.debug("check link state");
            notifyLinkStateChange(getActiveInterfaces());
        } catch (SocketException e) {
            SVClient.logSendDefectAsync(true, "can't check link state", e);
        }
    }

    private void notifyLinkStateChange(final ImmutableSet<NetworkInterface> current)
    {
        final ImmutableSet<NetworkInterface> previous = _ifaces;

        if (current.equals(previous)) return;

        l.info("ls prev " + previous.size() + " cur " + current.size());

        final Set<NetworkInterface> added = Sets.newHashSet(current);
        added.removeAll(previous);
        final Set<NetworkInterface> removed = Sets.newHashSet(previous);
        removed.removeAll(current);

        _notifier.notifyOnOtherThreads(new IListenerVisitor<ILinkStateListener>()
        {
            @Override
            public void visit(ILinkStateListener listener)
            {
                listener.onLinkStateChanged(previous, current, copyOf(added), copyOf(removed));
            }
        });

        _ifaces = current;
    }

    public final void start()
    {
        if (!_started.compareAndSet(false, true)) {
            return;
        }

        l.info("start lss thd");

        startDaemonThread("lss", new Runnable()
        {
            @Override
            public void run()
            {
                // Check link state, then wait for interface change, and then repeat on interface
                // changes. If waiting failed or not supported, fall back to polling.
                //
                // BUGBUG There is a small probability that we might lose change notifications
                // between two consecutive calls to waitForNetworkInterfaceChange(). A proper fix is
                // to use asynchronous callbacks. But calling back from C to Java is not straight-
                // forward (see libjingle-binding implementation).
                //
                // We didn't implement Driver.waitForNetworkInterfaceChange for UNIX OSes, assuming
                // getActiveInterfaces doesn't take too long or too much CPU on these OSes.
                // Otherwise, we should implement this method.
                //
                // noinspection InfiniteLoopStatement
                while (true) {
                    if (!_markedDown) {
                        checkLinkState();
                    }

                    // Only Windows has a proper implementation of waitForNetworkInterfaceChange.
                    if (Driver.waitForNetworkInterfaceChange() != DriverConstants.DRIVER_SUCCESS) {
                        ThreadUtil.sleepUninterruptable(DaemonParam.LINK_STATE_POLLING_INTERVAL);
                    }
                }
            }
        });
    }

    public void addListener(ILinkStateListener listener, Executor callbackExecutor)
    {
        _notifier.addListener(listener, callbackExecutor);
    }

    // IMPORTANT: the method is _not final_ because I want it to be mockable
    public void markLinksDown()
    {
        l.warn("mark down");
        _markedDown = true;
        notifyLinkStateChange(ImmutableSet.<NetworkInterface>of());
    }

    // IMPORTANT: the method is _not final_ because I want it to be mockable
    public void markLinksUp()
    {
        l.warn("mark up");
        _markedDown = false;
        checkLinkState();
    }
}
