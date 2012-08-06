/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.link;

import com.aerofs.lib.Util;
import com.aerofs.lib.notifier.IListenerVisitor;
import com.aerofs.lib.notifier.Notifier;
import com.google.common.collect.ImmutableSet;
import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Monitors the state of local NICs
 */
public abstract class AbstractNetworkLinkStateService implements INetworkLinkStateService
{
    protected static final Logger l = Util.l(AbstractNetworkLinkStateService.class);

    protected final Notifier<INetworkLinkStateListener> _notifier = Notifier.create();

    // hmm...probably should be public
    private ImmutableSet<NetworkInterface> _ifaces = ImmutableSet.of();

    protected static final ImmutableSet<NetworkInterface> getActiveInterfacesImpl()
            throws SocketException
    {
        //
        // [sigh] can't filter by MAC address either, since within a virtual machine
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
        //

        ImmutableSet.Builder<NetworkInterface> ifaceBuilder = ImmutableSet.builder();
        l.info("ls:ifs:");
        for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
            NetworkInterface iface = e.nextElement();
            l.info(iface + "(u:" + iface.isUp() + " l:" + iface.isLoopback() + " v:" +
                   iface.isVirtual() + " m:" + iface.supportsMulticast() + ")");

            // IMPORTANT: On Mac OS X, disabling an interface via Network Preferences
            // simply removes the interface's IPV4 address. This caused a situation where
            // we would remove IFprev(IPV4, IPV6) and then _readd_ IFnew(IPV6).
            // This caused Multicast::send to attempt to send packets through this
            // interface even though it wasn't 'active'.
            if (iface.isUp() && !iface.isLoopback() && !iface.isVirtual()) {
                Enumeration<InetAddress> ias = iface.getInetAddresses();
                int numIpV4Addresses = 0;
                while (ias.hasMoreElements()) {
                    if (!ias.nextElement().getHostAddress().contains(":")) {
                        numIpV4Addresses++;
                    }
                }

                l.info("ls:if " + iface.getDisplayName() + " v4:" + numIpV4Addresses);
                if (numIpV4Addresses > 0) ifaceBuilder.add(iface);
            }
        }
        return ifaceBuilder.build();
    }

    @Override
    public final void addListener_(INetworkLinkStateListener listener, Executor callbackExecutor)
    {
        _notifier.addListener(listener, callbackExecutor);
    }

    @Override
    public final void removeListener_(INetworkLinkStateListener listener)
    {
        _notifier.removeListener(listener);
    }

    /**
     * This method can be overridden by subclasses to return a smaller (larger?) set of interfaces
     * than actually exists
     *
     * @return a set of currently active network interfaces
     * @throws Exception if there is any problem getting the list of interfaces
     */
    protected ImmutableSet<NetworkInterface> getActiveInterfaces_()
        throws Exception
    {
        return getActiveInterfacesImpl();
    }

    /**
     * Recipe for checking the state of local NICs
     *
     * @throws Exception if there is any problem checking the link state; if an exception is thrown
     * no internal state is changed. This means a subsquent call to {@code checkLinkState_} acts as
     * if no prior call was made
     */
    protected final void checkLinkState_()
            throws Exception
    {
        // 1. get the list of currently active interfaces

        final ImmutableSet<NetworkInterface> current = getActiveInterfaces_();

        // 2. calculate the difference between the current and previous set

        final ImmutableSet<NetworkInterface> previous = _ifaces;

        // 3. bail out early if nothing has changed

        if (current.equals(previous)) {
            return;
        }

        // 4. calculate the difference between the current and previous set

        Set<NetworkInterface> added = new HashSet<NetworkInterface>(current);
        added.removeAll(previous);
        Set<NetworkInterface> removed = new HashSet<NetworkInterface>(previous);
        removed.removeAll(current);

        // 5. notify listeners of the difference

        notifyLinkStateServiceListeners_(current, previous, added, removed);

        // 6. update the current state

        // update _ifaces after the above event has executed successfully so
        // we can retry execution next time on any failures
        _ifaces = current;
    }

    /**
     * Notify all listeners of the {@link INetworkLinkStateService}
     *
     * @param current set of current local NICs
     * @param previous set of previous local NICs
     * @param added set of local NICs that were added (previous + added = current)
     * @param removed set of local NICs that were removed (previous - removed = current)
     */
    protected void notifyLinkStateServiceListeners_(final ImmutableSet<NetworkInterface> current,
            final ImmutableSet<NetworkInterface> previous, Set<NetworkInterface> added,
            Set<NetworkInterface> removed)
    {
        l.warn("ls prev " + previous.size() + " cur " + current.size());
        for (NetworkInterface iface : added) {
            l.info("ls:add:" + iface);
        }
        for (NetworkInterface iface : removed) {
            l.info("ls:rem:" + iface);
        }

        final ImmutableSet<NetworkInterface> immutableAdded = ImmutableSet.copyOf(added);
        final ImmutableSet<NetworkInterface> immutableRemoved = ImmutableSet.copyOf(removed);

        _notifier.notifyOnOtherThreads(new IListenerVisitor<INetworkLinkStateListener>()
        {
            @Override
            public void visit(INetworkLinkStateListener listener)
            {
                listener.onLinkStateChanged_(immutableAdded, immutableRemoved, current, previous);
            }
        });
    }

    public final boolean isUp_()
    {
        return !_ifaces.isEmpty();
    }
}
