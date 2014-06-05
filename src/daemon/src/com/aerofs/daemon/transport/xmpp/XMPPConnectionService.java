/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.Base64;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.daemon.lib.Listeners;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.ImmutableSet;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aerofs.daemon.transport.lib.TransportDefects.DEFECT_NAME_XSC_CONNECTION_ALREADY_REPLACED;
import static com.aerofs.daemon.transport.lib.TransportDefects.DEFECT_NAME_XSC_CONNECTION_EXISTED_ON_LINK_DOWN_TO_UP_LSC;
import static com.aerofs.daemon.transport.lib.TransportUtil.newConnectedSocket;
import static com.aerofs.lib.Util.exponentialRetryNewThread;
import static com.google.common.collect.Maps.newConcurrentMap;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static org.jivesoftware.smack.packet.IQ.Type.GET;

public final class XMPPConnectionService implements ILinkStateListener
{
    public static final String XMPP_PING_STANZA = "<ping xmlns='urn:xmpp:ping'/>";


    public static interface IXMPPConnectionServiceListener
    {
        void xmppServerConnected(XMPPConnection conn) throws Exception;
        void xmppServerDisconnected();
    }

    static
    {
        //XMPPConnection.DEBUG_ENABLED = true;
    }

    // 64 bytes
    private static final byte[] XMPP_PASSWORD_SALT = {
        (byte)0xcc, (byte)0xd9, (byte)0x82, (byte)0x0d,
        (byte)0xf2, (byte)0xf1, (byte)0x4a, (byte)0x56,
        (byte)0x0a, (byte)0x70, (byte)0x28, (byte)0xbe,
        (byte)0x91, (byte)0xd6, (byte)0xb8, (byte)0x51,
        (byte)0x78, (byte)0x03, (byte)0xc4, (byte)0x8f,
        (byte)0x30, (byte)0x8b, (byte)0xdd, (byte)0xbf,
        (byte)0x2d, (byte)0x80, (byte)0x45, (byte)0x75,
        (byte)0xff, (byte)0x2d, (byte)0x4f, (byte)0x55,
        (byte)0x0c, (byte)0x2e, (byte)0x1b, (byte)0x2d,
        (byte)0x80, (byte)0x77, (byte)0x73, (byte)0x95,
        (byte)0x25, (byte)0x7c, (byte)0xf2, (byte)0x8e,
        (byte)0xa5, (byte)0x49, (byte)0x5c, (byte)0xf2,
        (byte)0xa6, (byte)0x4a, (byte)0x64, (byte)0x31,
        (byte)0x3a, (byte)0xb3, (byte)0x04, (byte)0x48,
        (byte)0xd7, (byte)0x89, (byte)0xeb, (byte)0xd6,
        (byte)0x17, (byte)0x7e, (byte)0x56, (byte)0x81
    };

    private static final Logger l = Loggers.getLogger(XMPPConnectionService.class);

    // FIXME (AG): the right thing is not to leak connection outside at all!
    private volatile XMPPConnection connection = null; // protect via this (volatile so that connection() can work properly)
    private volatile boolean linkUp = false;

    private final RockLog rocklog;
    private final DID localdid;
    private final InetSocketAddress xmppServerAddress;
    private final String xmppUser;
    private final String xmppServerDomain;
    private final String resource;
    private String xmppJid;
    private final String xmppPassword; // sha256(scrypt(p|u)|XMPP_PASSWORD_SALT)
    private final long linkStateChangePingInterval;
    private final int maxPingsBeforeDisconnection;
    private final long initialConnectRetryInterval;
    private final long maxConnectRetryInterval;
    private final Timer timer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger xscThreadId = new AtomicInteger(0);
    private final AtomicBoolean connectionInProgress = new AtomicBoolean(false);
    private final ConcurrentMap<String, TimerTask> outstandingPings = newConcurrentMap();
    private final Listeners<IXMPPConnectionServiceListener> _listeners = Listeners.create();

    public XMPPConnectionService(
            String transportId,
            DID localdid,
            InetSocketAddress xmppServerAddress,
            String xmppServerDomain,
            String resource,
            byte[] scrypted,
            long linkStateChangePingInterval,
            int maxPingsBeforeDisconnection,
            long initialConnectRetryInterval,
            long maxConnectRetryInterval,
            RockLog rocklog,
            LinkStateService linkStateService)
    {
        this.localdid = localdid;
        this.xmppServerAddress = xmppServerAddress;
        this.xmppUser = JabberID.did2user(this.localdid);
        this.xmppServerDomain = xmppServerDomain;
        this.resource = resource;
        this.xmppJid = JabberID.did2FormAJid(localdid, xmppServerDomain, resource);
        this.xmppPassword = Base64.encodeBytes(SecUtil.hash(scrypted, XMPP_PASSWORD_SALT));
        this.linkStateChangePingInterval = linkStateChangePingInterval;
        this.maxPingsBeforeDisconnection = maxPingsBeforeDisconnection;
        this.initialConnectRetryInterval = initialConnectRetryInterval;
        this.maxConnectRetryInterval = maxConnectRetryInterval;
        this.rocklog = rocklog;
        this.timer = new Timer(transportId + "-pt", true);

        linkStateService.addListener(this, sameThreadExecutor()); // our implementation of onLinkStateChanged is thread-safe
    }

    public void start()
    {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        l.info("starting xmpp connection service");

        connect(false);
    }

    public void stop()
    {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        l.info("stopping xmpp connection service");

        XMPPConnection connectionToClose = connection;
        if (connectionToClose != null && connectionToClose.isConnected()) {
            safeDisconnect(connection);
        }

        timer.cancel();
    }

    public void addListener(IXMPPConnectionServiceListener listener)
    {
        _listeners.add(listener);
    }

    /**
     * @throws XMPPException if not connected to the server
     * FIXME: remove this method since I don't think it's a good idea, and I'm not sure it's thread-safe
     * IMPORTANT: even if you get back a connection it may be invalid
     */
    public XMPPConnection conn() throws XMPPException
    {
        XMPPConnection c = connection;
        if (c == null) throw new XMPPException("disconnected");
        return c;
    }

    // FIXME (AG): replace this reachability code with ping
    public boolean isReachable()
            throws IOException
    {
        Socket s = null;
        try {
            s = newConnectedSocket(xmppServerAddress, (int)(2 * C.SEC));
            return true;
        } catch (IOException e) {
            l.warn("fail xmpp reachability check", e);
            throw e;
        } finally {
            if (s != null) try {
                s.close();
            } catch (IOException e) {
                l.warn("fail close reachability socket with err:{}", e.getMessage());
            }
        }
    }

    /**
     * Returns the credentials required to log into the XMPP server
     *
     * @return <pre>sha256(scrypt(p|u)|u)</pre>
     */
    public String getXmppPassword()
    {
        return xmppPassword;
    }

    @Override
    public synchronized void onLinkStateChanged(
            ImmutableSet<NetworkInterface> previous,
            ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed)
    {
        // IMPORTANT: ___ALWAYS___ ALLOW THE linkUp FLAG TO BE SET!
        //
        // depending on initialization order it's possible for
        // onLinkStateChanged() to be called before start() is called.
        // start() will _only_ start connecting if linkUp = true.
        // if this method does an early return because running == false,
        // then linkUp will never be set, and a subsequent call to
        // start() will be a noop until another link-state-change occurs.
        // this would, obviously, be very, very bad.
        //
        // so... ___ALWAYS__ ALLOW THE linkUp FLAG TO BE SET!

        boolean wasUp = linkUp;
        linkUp = !current.isEmpty();

        l.debug("wasUp:{} linkUp:{}", wasUp, linkUp);

        // then only check if we're running...

        if (!running.get()) {
             return;
        }

        XMPPConnection connectionToCheck;
        boolean connected;
        synchronized (this) {
            connectionToCheck = connection;
            connected = (connectionToCheck != null && connectionToCheck.isConnected());
            // can't set connection to null if !up because disconnection handler won't run
        }

        if (wasUp && !linkUp && connected) {
            safeDisconnect(connectionToCheck);
        } else if (!wasUp && linkUp) {
            if (connected) {
                l.warn("unexpected connection on lsc transition");
                rocklog.newDefect(DEFECT_NAME_XSC_CONNECTION_EXISTED_ON_LINK_DOWN_TO_UP_LSC).send();
                safeDisconnect(connectionToCheck);
            }
            connect(false);
        } else if (wasUp && linkUp && connected && !removed.isEmpty() && outstandingPings.isEmpty()) {
            schedulePing(connectionToCheck, 1);
        }
    }

    private void safeDisconnect(XMPPConnection badConnection)
    {
        l.trace("safe disconnect {}", badConnection.getConnectionID());

        try {
            badConnection.disconnect();
        } catch (NullPointerException e) {
            l.warn("fail disconnect cleanly err:npe");
        } catch (Exception e) {
            l.warn("fail disconnect cleanly err:{}", e.getMessage());
        }
    }

    /**
     * This method will force a ping. Use it for unit tests only
     * (also why it's package private)!
     */
    void ping()
    {
        XMPPConnection connectionToCheck = connection;
        if (connectionToCheck == null || !connectionToCheck.isConnected()) {
            throw new IllegalStateException("invalid connection");
        }

        schedulePing(connectionToCheck, 1);
    }

    private void schedulePing(final XMPPConnection connectionToCheck, final int pingCount)
    {
        l.trace("schedule ping:{}", pingCount);

        if (!connectionToCheck.isConnected()) {
            return;
        }

        final String pingId = UUID.randomUUID().toString();

        TimerTask pingTask = new TimerTask()
        {
            @Override
            public void run()
            {
                try {
                    if (outstandingPings.remove(pingId) != null) { // the ping wasn't answered
                        if (pingCount < maxPingsBeforeDisconnection) {
                            schedulePing(connectionToCheck, pingCount + 1);
                        } else {
                            throw new Exception("failed " + maxPingsBeforeDisconnection + " pings; connection dead");
                        }
                    }
                } catch (Exception e) { // this additional catch block is not overkill
                    // I know it looks like it, but if we don't catch an exception
                    // and let it leak into the timer thread, then the timer will be
                    // cancelled and nothing will work anymore
                    safeDisconnect(connectionToCheck);
                }
            }
        };

        try {
            Packet ping = newPing(pingId);
            outstandingPings.put(ping.getPacketID(), pingTask);
            connectionToCheck.sendPacket(ping);
            timer.schedule(pingTask, linkStateChangePingInterval);
        } catch (Exception e) {
            outstandingPings.remove(pingId);
            safeDisconnect(connectionToCheck);
        }
    }

    private Packet newPing(String pingId)
    {
        IQ ping = new IQ()
        {
            @Override
            public String getChildElementXML()
            {
                return XMPP_PING_STANZA;
            }
        };
        ping.setType(GET);
        ping.setFrom(xmppJid);
        ping.setPacketID(pingId);
        return ping;
    }

    private XMPPConnection newXMPPConnection()
    {
        // The xmpp server address is an unresolved hostname.
        // We avoid resolving the hostname ourselves and let
        // SMACK do the DNS query on its thread.
        ConnectionConfiguration cc = new ConnectionConfiguration(xmppServerAddress.getHostName(), xmppServerAddress.getPort());
        cc.setServiceName(xmppServerDomain);
        cc.setSecurityMode(SecurityMode.required);
        cc.setSelfSignedCertificateEnabled(true);
        cc.setVerifyChainEnabled(false);
        cc.setVerifyRootCAEnabled(false);
        cc.setReconnectionAllowed(false);
        cc.setRosterLoadedAtLogin(false);
        // ejabberd doesn't support compression when using STARTTLS,
        // so this would cause a 25-second pause on startup
        cc.setCompressionEnabled(false);

        return new XMPPConnection(cc);
    }

    private void connect(final boolean initialDelay)
    {
        if (!connectionInProgress.compareAndSet(false, true)) { // someone else is already attempting to connect - bail, and let them finish
            l.warn("connection attempt in progress");
            return;
        }

        if (initialDelay) {
            l.info("connect: use initial delay:{}", initialConnectRetryInterval);
        }

        exponentialRetryNewThread("x-" + xscThreadId.getAndIncrement(), initialConnectRetryInterval, maxConnectRetryInterval, new Callable<Void>()
        {
            @Override
            public Void call()
                    throws Exception
            {
                if (!running.get()) {
                    l.info("service shut down - terminating connect");
                    return terminateConnectionAttempt();
                }

                outstandingPings.clear();

                if (initialDelay) {
                    l.debug("reconnect in {}", initialConnectRetryInterval);
                    ThreadUtil.sleepUninterruptable(initialConnectRetryInterval);
                }

                l.debug("connecting");

                try {
                    if (!linkUp) {
                        l.debug("link down - terminating connect");
                    } else {
                        synchronized (XMPPConnectionService.this) { connectInternal(); }
                    }

                    return terminateConnectionAttempt();
                } catch (IllegalStateException e) {
                    // this is what smack throws if we
                    // attempt to send a message and the system
                    // has disconnected underneath us
                    if (e.getMessage().equals("Not connected to server.")) {
                        throw new XMPPException("not connected to XMPP server");
                    } else {
                        // if it's some other ISE that we're not aware of,
                        // then simply throw and let it crash the daemon,
                        // so that a defect can be generated
                        throw e;
                    }
                } catch (XMPPException e) {
                    if (e.getXMPPError() != null) {
                        // 502: remote-server-error(502): java.net.ConnectException: Operation timed out
                        // 504: remote-server-error(504): connection refused
                        int errorCode = e.getXMPPError().getCode();
                        if (errorCode == 502 || errorCode == 504) {
                            throw new IOException(String.valueOf(errorCode));
                        }
                    }

                    throw e;
                }
            }

            private Void terminateConnectionAttempt()
            {
                connectionInProgress.set(false);
                return null;
            }
        }, Exception.class);
    }

    private void connectInternal()
            throws Exception
    {
        XMPPConnection newConnection = null;
        try {
            // are we replacing a valid connection?
            if (connection != null && connection.isAuthenticated()) {
                l.warn("replacing old connection");
            }

            newConnection = createNewConnectionAndLogin();
            addPingListener(newConnection);

            // for legacy reasons (basically I don't have time to refactor the code) Multicast
            // accesses connection directly. Since Multicast runs on a different thread the moment we
            // assign a new value to connection it _may_ be accessed by Multicast (i.e. even before
            // a listener is added

            connection = newConnection; // this is the point at which changes are visible

            // I would prefer to only set connection _after_ calling listener.connected, but apparently
            // Multicast.java uses connection() internally...
            notifyListenersOfNewXMPPConnection(newConnection);

            // FIXME: we need to verify that the connection is actually valid prior to adding a listener
            // we don't rely on Smack API's connect capability as experiments
            // showed it's not reliable. See also newXMPPConnection()
            addXMPPConnectionDisconnectionListener(newConnection);
        } catch (Exception e) {
            if (newConnection != null) {
                safeDisconnect(newConnection);
            }

            throw e;
        }
    }

    private XMPPConnection createNewConnectionAndLogin()
            throws XMPPException
    {
        final XMPPConnection newConnection = newXMPPConnection();

        l.debug("connecting to " + newConnection.getHost() + ":" + newConnection.getPort());
        newConnection.connect();

        l.trace("logging in as " + xmppJid); // done to show relationship
        newConnection.login(xmppUser, getXmppPassword(), resource);

        l.trace("logged in");
        return newConnection;
    }

    private void addPingListener(XMPPConnection newConnection)
    {
        newConnection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(@Nullable Packet packet)
            {
                if (packet == null || packet.getFrom() == null) {
                    return;
                }

                if (packet.getFrom().equals(JabberID.did2BareJid(localdid, xmppServerDomain))) {
                    String packetId = packet.getPacketID();
                    l.trace("recv self-directed response with packet id:{}", packetId);

                    if (packetId != null && !packetId.isEmpty()) {
                        TimerTask pingTask = outstandingPings.remove(packetId);
                        if (pingTask != null) {
                            pingTask.cancel();
                        }
                    }
                }
            }
        }, new PacketTypeFilter(IQ.class));
    }

    private void notifyListenersOfNewXMPPConnection(XMPPConnection newConnection)
            throws Exception
    {
        try {
            for (IXMPPConnectionServiceListener listener : _listeners) {
                listener.xmppServerConnected(newConnection);
            }
        } catch (Exception e) {
            connection = null;
            throw e;
        }
    }

    private void addXMPPConnectionDisconnectionListener(final XMPPConnection newConnection)
    {
        l.trace("adding listener");

        newConnection.addConnectionListener(new ConnectionListener()
        {
            @Override
            public void connectionClosed()
            {
                l.info("connection closed");
                runConnectionClosedTasks();
            }

            @Override
            public void connectionClosedOnError(Exception e)
            {
                l.warn("connection closed", e);
                runConnectionClosedTasks();
            }

            private void runConnectionClosedTasks()
            {
                outstandingPings.clear();

                // XXX: the previous comment here referenced a deadlock - I don't _think_ one is possible...
                boolean replaced = false;
                synchronized (XMPPConnectionService.this) {
                    if (connection == newConnection) {
                        try {
                            l.trace("notifying listeners of disconnection");
                            for (IXMPPConnectionServiceListener listener : _listeners) {
                                listener.xmppServerDisconnected();
                            }
                        } finally {
                            connection = null;
                            replaced = true;
                            l.trace("connection replaced");
                        }
                    } else {
                        l.warn("connection previously replaced");
                        rocklog.newDefect(DEFECT_NAME_XSC_CONNECTION_ALREADY_REPLACED).send();
                    }
                }

                newConnection.removeConnectionListener(this); // remove so that this handler isn't called again
                safeDisconnect(newConnection);

                if (replaced) {
                    if (!running.get()) {
                        l.info("service shut down - not attempting reconnect");
                        return;
                    }

                    if (linkUp) {
                        connect(true);
                    } else {
                        l.info("link down - not attempting reconnect");
                    }
                }
            }

            // unused interface methods
            // (since our config prevents Smack from automatically reconnecting)
            @Override
            public void reconnectingIn(int arg0) { }
            @Override
            public void reconnectionFailed(Exception arg0) { }
            @Override
            public void reconnectionSuccessful() { }
        });
    }
}
