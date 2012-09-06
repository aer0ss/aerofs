/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.link;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.notifier.IListenerVisitor;
import com.aerofs.lib.notifier.Notifier;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.swig.driver.Driver;
import com.aerofs.swig.driver.DriverConstants;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * This class monitors the state of local NICs
 */
public abstract class AbstractLinkStateService implements ILinkStateService
{
    protected static final Logger l = Util.l(AbstractLinkStateService.class);

    private final Notifier<ILinkStateListener> _notifier = Notifier.create();
    private ImmutableSet<NetworkInterface> _ifaces = ImmutableSet.of();
    // whether we are told to say that all interfaces on the machine are down
    private boolean _markedDown;

    /**
     * The implementation of this method should call the runnable on the thread where other methods
     * of this class is called. This method is called from a stand-alone thread.
     */
    public abstract void execute(Runnable runnable);

    private final ImmutableSet<NetworkInterface> getActiveInterfaces_()
            throws SocketException
    {
        if (_markedDown) return ImmutableSet.of();

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

        for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
                e.hasMoreElements();) {
            NetworkInterface iface = e.nextElement();

            // cache interface information as local variables, since some queries on some Windows
            // computers are very slow. Experiments showed that getName and isUp can take 100ms
            // each on computers with VirtualBox installed :S
            String name = iface.getName();
            boolean isUp = iface.isUp();
            boolean isLoopback = iface.isLoopback();
            boolean isVirtual = iface.isVirtual();

            if (l.isInfoEnabled()) {
                l.info(name + "(u:" + isUp + " l:" + isLoopback + " v:" + isVirtual + ")");
            }

            // IMPORTANT: On Mac OS X, disabling an interface via Network Preferences
            // simply removes the interface's IPV4 address. This caused a situation where
            // we would remove IFprev(IPV4, IPV6) and then _readd_ IFnew(IPV6).
            // This caused Multicast::send to attempt to send packets through this
            // interface even though it wasn't 'active'.
            if (isUp && !isLoopback && !isVirtual) {
                Enumeration<InetAddress> ias = iface.getInetAddresses();
                while (ias.hasMoreElements()) {
                    if (!ias.nextElement().getHostAddress().contains(":")) {
                        // it is an IPv4 address
                        ifaceBuilder.add(iface);
                        break;
                    }
                }
            }
        }

        return ifaceBuilder.build();
    }

    private final void checkLinkState_() throws SocketException
    {
        // getActiveInterfaces_ shouldn't take too long on OSX and Linux. Otherwise, we'd better
        // implement Driver.waitForNetworkInterfaceChange for these OSes sooner.
        // TODO (WW) remove this debugging facility
        long start = OSUtil.isWindows() ? 0 : System.currentTimeMillis();
        final ImmutableSet<NetworkInterface> current = getActiveInterfaces_();
        if (!OSUtil.isWindows()) {
            long duration = System.currentTimeMillis() - start;
            if (duration > 50) {
                Exception e = new Exception("getActiveInterfaces too long: " + duration);
                SVClient.logSendDefectAsync(true, "getActiveInterfaces too long", e);
            }
        }

        final ImmutableSet<NetworkInterface> previous = _ifaces;
        if (current.equals(previous)) return;

        l.warn("ls prev " + previous.size() + " cur " + current.size());

        // notify listeners of the difference
        final Set<NetworkInterface> added = Sets.newHashSet(current);
        added.removeAll(previous);
        final Set<NetworkInterface> removed = Sets.newHashSet(previous);
        removed.removeAll(current);
        _notifier.notifyOnOtherThreads(new IListenerVisitor<ILinkStateListener>() {
            @Override
            public void visit(ILinkStateListener listener)
            {
                listener.onLinkStateChanged_(ImmutableSet.copyOf(added),
                        ImmutableSet.copyOf(removed), current, previous);
            }
        });

        // update _ifaces after the above event has executed successfully so
        // we can retry execution next time on any failures
        _ifaces = current;
    }

    @Override
    public final void start_()
    {
        Util.startDaemonThread("lss", new Runnable() {
            @Override
            public void run()
            {
                Runnable runCheckLinkState = new Runnable() {
                    @Override
                    public void run()
                    {
                        try {
                            checkLinkState_();
                        } catch (SocketException e) {
                            SVClient.logSendDefectAsync(true, "can't check link state", e);
                        }
                    }
                };

                // Check link state, then wait for interface change, and then repeat on interface
                // changes. If waiting failed or not supported, fall back to polling.
                //
                // BUGBUG There is a small probability that we might lose change notifications
                // between two consecutive calls to waitForNetworkInterfaceChange(). A proper fix is
                // to use asynchronous callbacks. But calling back from C to Java is not straight-
                // forward (see libjingle-binding implementation).
                //
                while (true) {
                    // TODO (WW) remove the following log line.
                    if (OSUtil.isWindows()) l.warn("check link state");
                    execute(runCheckLinkState);
                    if (Driver.waitForNetworkInterfaceChange() != DriverConstants.DRIVER_SUCCESS) {
                        Util.sleepUninterruptable(DaemonParam.LINK_STATE_POLLING_INTERVAL);
                    }
                }
            }
        });
    }

    @Override
    public final void addListener_(ILinkStateListener listener, Executor callbackExecutor)
    {
        _notifier.addListener(listener, callbackExecutor);
    }

    @Override
    public final void removeListener_(ILinkStateListener listener)
    {
        _notifier.removeListener(listener);
    }

    public final boolean isUp_()
    {
        return !_ifaces.isEmpty();
    }

    /**
     * Mark all the links as if they are down. This method is used to pause syncing activities
     */
    public void markLinksDown_()
            throws Exception
    {
        l.warn("mark down");
        _markedDown = true;
        checkLinkState_();
    }

    /**
     * Undo markLinksDown_().
     */
    public void markLinksUp_()
            throws Exception
    {
        l.warn("mark up");
        _markedDown = false;
        checkLinkState_();
    }
}
