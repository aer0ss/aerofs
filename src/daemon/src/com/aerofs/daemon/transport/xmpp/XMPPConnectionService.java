/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseParam.XMPP;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.daemon.lib.Listeners;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.rocklog.RockLog;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aerofs.daemon.transport.exception.TransportDefects.DEFECT_NAME_XSC_CONNECTION_ALREADY_REPLACED;
import static com.aerofs.daemon.transport.lib.TransportUtil.newConnectedSocket;
import static com.aerofs.lib.LibParam.EXP_RETRY_MIN_DEFAULT;
import static com.aerofs.lib.Util.exponentialRetryNewThread;

public final class XMPPConnectionService implements IDumpStatMisc
{
    public static interface IXMPPConnectionServiceListener
    {
        void xmppServerConnected(XMPPConnection conn) throws XMPPException;
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
    private final String localjid;
    private final String resource;
    private final String xmppPassword; // sha256(scrypt(p|u)|XMPP_PASSWORD_SALT)
    private final AtomicInteger xscThreadId = new AtomicInteger(0);
    private final AtomicBoolean connectionInProgress = new AtomicBoolean(false);
    private final Listeners<IXMPPConnectionServiceListener> _listeners = Listeners.create();

    public XMPPConnectionService(DID localdid, String resource, byte[] scrypted, RockLog rocklog)
    {
        this.localdid = localdid;
        this.localjid = JabberID.did2user(this.localdid);
        this.resource = resource;
        this.xmppPassword = Base64.encodeBytes(SecUtil.hash(scrypted, XMPP_PASSWORD_SALT));
        this.rocklog = rocklog;
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

    public boolean isReachable()
            throws IOException
    {
        Socket s = null;
        try {
            s = newConnectedSocket(XMPP.ADDRESS, (int)(2 * C.SEC));
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

    public void linkStateChanged(boolean up)
    {
        boolean wasup = linkUp;
        linkUp = up;

        //
        // it's not clear to me if it's actually worth the bother to check if
        // the connection is valid and disconnect it. Maybe it's better just to
        // leave it around and let it get disconnected at its own sweet pace
        //

        XMPPConnection c;
        boolean connected;
        synchronized (this) {
            c = connection;
            connected = (c != null && c.isConnected());
            // can't set connection to null if !up because disconnection handler won't run
        }

        if (!up && c != null && connected) {
            try {
                c.disconnect();
            } catch (NullPointerException e) {
                l.warn("smk npe");
            }
        } else if (!wasup && up) {
            startConnect(false);
        }
    }

    private XMPPConnection newXMPPConnection()
    {
        // The xmpp server address is an unresolved hostname.
        // We avoid resolving the hostname ourselves and let
        // SMACK do the DNS query on its thread.
        InetSocketAddress address = XMPP.ADDRESS;
        ConnectionConfiguration cc = new ConnectionConfiguration(address.getHostName(), address.getPort());
        cc.setServiceName(XMPP.SERVER_DOMAIN);
        cc.setSecurityMode(SecurityMode.required);
        cc.setSelfSignedCertificateEnabled(true);
        cc.setVerifyChainEnabled(false);
        cc.setVerifyRootCAEnabled(false);
        cc.setReconnectionAllowed(false);
        // ejabberd doesn't support compression when using STARTTLS,
        // so this would cause a 25-second pause on startup
        cc.setCompressionEnabled(false);

        return new XMPPConnection(cc);
    }

    private void startConnect(final boolean initialDelay)
    {
        if (!connectionInProgress.compareAndSet(false, true)) { // someone else is already attempting to connect - bail, and let them finish
            l.warn("connection attempt in progress");
            return;
        }

        l.info("startConnect: use initial delay:{}", initialDelay);

        exponentialRetryNewThread("xsc-" + xscThreadId.getAndIncrement(), new Callable<Void>()
        {
            @Override
            public Void call()
                    throws Exception
            {
                if (initialDelay) {
                    l.info("reconnect in {}", EXP_RETRY_MIN_DEFAULT);
                    ThreadUtil.sleepUninterruptable(EXP_RETRY_MIN_DEFAULT);
                }

                l.info("connecting");

                try {
                    if (!linkUp) {
                        l.info("link down. do not attempt connect.");
                    } else {
                        synchronized (XMPPConnectionService.this) { connect_(); }
                    }

                    connectionInProgress.set(false);
                    return null;
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
        }, Exception.class);
    }

    private void connect_() throws XMPPException
    {
        try {
            connectImpl_();
        } catch (IllegalStateException e) {
            throw new XMPPException(e);
        }
    }

    // should be accessed by only one thread at a time. we don't want to mess up
    // with the xmpp library. and access to connection need synchronization, too.
    private void connectImpl_() throws XMPPException
    {
        //
        // check if we have a valid connection already
        //

        if (connection != null && connection.isAuthenticated()) {
            l.warn("beginning process to replace old connection");
        }

        //
        // create a new connection and log in
        //

        final XMPPConnection newConnection = newXMPPConnection();
        l.info("connecting to " + newConnection.getHost() + ":" + newConnection.getPort());
        newConnection.connect();

        l.info("logging in as " + JabberID.did2FormAJid(localdid, resource)); // done to show relationship
        newConnection.login(localjid, getXmppPassword(), resource);
        l.info("logged in");

        // for legacy reasons (basically I don't have time to refactor the code) Multicast
        // accesses connection directly. Since Multicast runs on a different thread the moment we
        // assign a new value to connection it _may_ be accessed by Multicast (i.e. even before
        // a listener is added

        connection = newConnection; // this is the point at which changes are visible

        //
        // notify listener of connection
        //

        // I would prefer to only set connection _after_ calling listener.connected, but apparently
        // Multicast.java uses connection() internally...
        try {
            for (IXMPPConnectionServiceListener listener : _listeners) {
                listener.xmppServerConnected(newConnection);
            }
        } catch (XMPPException e) {
            connection = null;
            throw e;
        } catch (RuntimeException e) {
            connection = null;
            throw e;
        }

        //
        // add disconnection listener
        //

        // FIXME: we need to verify that the connection is actually valid prior to adding a listener
        // we don't rely on Smack API's connect capability as experiments
        // showed it's not reliable. See also newXMPPConnection()
        l.info("adding listener");
        newConnection.addConnectionListener(new ConnectionListener()
        {
            @Override
            public void connectionClosed()
            {
                l.info("connection closed");
                disconnect();
            }

            @Override
            public void connectionClosedOnError(Exception e)
            {
                l.warn("connection closed with err:{}", e.getMessage());
                disconnect();
            }

            private void disconnect()
            {
                // XXX: the previous comment here referenced a deadlock - I don't _think_ one is possible...
                boolean replaced = false;
                synchronized (XMPPConnectionService.this) {
                    if (connection == newConnection) {
                        try {
                            l.info("notifying listeners of disconnection");
                            for (IXMPPConnectionServiceListener listener : _listeners) {
                                listener.xmppServerDisconnected();
                            }
                        } finally {
                            connection = null;
                            replaced = true;
                            l.info("connection replaced");
                        }
                    } else {
                        l.warn("connection already replaced");
                        rocklog.newDefect(DEFECT_NAME_XSC_CONNECTION_ALREADY_REPLACED).send();
                    }
                }

                newConnection.removeConnectionListener(this); // remove so that this handler isn't called again
                newConnection.disconnect();

                if (replaced) {
                    if (linkUp) {
                        l.info("attempt reconnect");
                        startConnect(true);
                    } else {
                        l.info("link is down. don't reconnect.");
                    }
                }
            }

            //
            // unused interface methods (since our config prevents Smack from
            // automatically reconnecting)
            //

            @Override
            public void reconnectingIn(int arg0)
            {
            }

            @Override
            public void reconnectionFailed(Exception arg0)
            {
            }

            @Override
            public void reconnectionSuccessful()
            {
            }
        });
    }

    @Override
    public synchronized void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        if (connection == null || !connection.isConnected()) {
            ps.println(indent + "disconnected");
        } else {
            ps.println(indent + "auth'ed " + connection.isAuthenticated() +
                    ", secure " + connection.isSecureConnection() +
                    ", tls " + connection.isUsingTLS() +
                    ", compression: " + connection.isUsingCompression());
        }
    }
}
