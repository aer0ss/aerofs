/*
 * Created by alleng, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseParam.Xmpp;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.JabberID;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import org.slf4j.Logger;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;

import java.io.PrintStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Set;
import java.util.concurrent.Callable;

public class XMPPServerConnection implements IDumpStatMisc
{
    public static interface IXMPPServerConnectionWatcher
    {
        void xmppServerConnected(XMPPConnection conn) throws XMPPException;

        void xmppServerDisconnected();
    }

    static
    {
        //XMPPConnection.DEBUG_ENABLED = true;
    }

    public XMPPServerConnection(String resource, IXMPPServerConnectionWatcher watcher)
    {
        _resource = resource;
        _watcher = watcher;
    }

    synchronized boolean ready()
    {
        return _conn != null && _conn.isConnected() && _conn.isAuthenticated();
   }

    /**
     * @throws XMPPException if not connected to the server
     * FIXME: remove this method since I don't think it's a good idea, and I'm not sure it's thread-safe
     * IMPORTANT: even if you get back a conn it may be invalid
     */
    public XMPPConnection conn() throws XMPPException
    {
        XMPPConnection c = _conn;
        if (c == null) throw new XMPPException("disconnected");
        return c;
    }

    public void linkStateChanged(Set<NetworkInterface> cur)
    {
        boolean up = !XMPP.allLinksDown(cur);

        boolean wasup = _linkUp;
        _linkUp = up;

        //
        // it's not clear to me if it's actually worth the bother to check if
        // the connection is valid and disconnect it. Maybe it's better just to
        // leave it around and let it get disconnected at its own sweet pace
        //

        XMPPConnection c;
        boolean connected;
        synchronized (this) {
            c = _conn;
            connected = (c != null && c.isConnected());
            // can't set _conn to null if !up because disconnection handler won't run
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

    private XMPPConnection newConnection()
    {
        // The xmpp server address is an unresolved hostname.
        // We avoid resolving the hostname ourselves and let
        // SMACK do the DNS query on its thread.
        InetSocketAddress address = Xmpp.ADDRESS.get();
        ConnectionConfiguration cc = new ConnectionConfiguration(
                address.getHostName(), address.getPort());
        cc.setServiceName(Xmpp.SERVER_DOMAIN.get());
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
        l.info("startConnect: delay=" + initialDelay);
        // TODO use scheduler instead of threads?
        Util.exponentialRetryNewThread("xsct", new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    if (initialDelay) {
                        l.info("reconnect in " + LibParam.EXP_RETRY_MIN_DEFAULT);
                        ThreadUtil.sleepUninterruptable(LibParam.EXP_RETRY_MIN_DEFAULT);
                    }
                    l.info("connecting");

                    try {
                        if (!_linkUp) {
                            l.info("link down. do not attempt connect.");
                            return null;
                        }
                        synchronized (XMPPServerConnection.this) { connect_(); }

                    } catch (XMPPException e) {
                        l.warn("error", e);
                        // 502: remote-server-error(502): java.net.ConnectException: Operation timed out
                        // 504: remote-server-error(504): connection refused
                        if (e.getXMPPError() != null) {
                            int errorCode = e.getXMPPError().getCode();
                            if (errorCode == 502 || errorCode == 504) {
                                throw new Exception(String.valueOf(errorCode));
                            }
                        }

                        throw e;

                    } catch (Exception e) {
                        l.error(Util.e(e, ConnectException.class));
                    }

                    return null;
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
    // with the xmpp library. and access to _conn need synchronization, too.
    private void connectImpl_() throws XMPPException
    {
        //
        // check if we have a valid connection already
        //

        if (_conn != null && _conn.isAuthenticated()) {
            l.warn("beginning process to replace old connection");
        }

        //
        // create a new connection and log in
        //

        XMPPConnection c = newConnection();
        l.info("connecting to " + c.getHost() + ":" + c.getPort());
        c.connect();

        l.info("logging in as " + JabberID.did2FormAJid(Cfg.did(), _resource)); // done to show relationship
        c.login(_user, shaedXMPP(), _resource);
        l.info("logged in");

        // for legacy reasons (basically I don't have time to refactor the code) Multicast
        // accesses conn directly. Since Multicast runs on a different thread the moment we
        // assign a new value to _conn it _may_ be accessed by Multicast (i.e. even before
        // a listener is added

        _conn = c; // this is the point at which changes are visible

        //
        // notify watcher of connection
        //

        // I would prefer to only set _conn _after_ calling _watcher.connected, but apparently
        // Multicast.java uses conn() internally...
        try {
            if (_watcher != null) {
                _watcher.xmppServerConnected(c);
            }
        } catch (XMPPException e) {
            _conn = null;
            throw e;
        } catch (RuntimeException e) {
            _conn = null;
            throw e;
        }

        //
        // add disconnection listener
        //

        // FIXME: we need to verify that the connection is actually valid prior to adding a listener
        // we don't rely on Smack API's connect capability as experiments
        // showed it's not reliable. See also newConnection()
        l.info("adding listener");
        _conn.addConnectionListener(new ConnectionListener()
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
                l.warn("connection closed: " + e);
                disconnect();
            }

            private void disconnect()
            {
                assert _c != null : "null listener conn";

                // XXX: the previous comment here referenced a deadlock - I don't _think_ one is possible...
                boolean replaced = false;
                synchronized (XMPPServerConnection.this) {
                    if (_conn == _c) {
                        try {
                            l.info("notifying listeners of disconnection");
                            if (_watcher != null) {
                                _watcher.xmppServerDisconnected();
                            }
                        } finally {
                            _conn = null;
                            replaced = true;
                            l.info("conn replaced");
                        }
                    } else {
                        l.info("connection already replaced");
                    }
                }

                _c.removeConnectionListener(this); // remove so that this handler isn't called again
                _c.disconnect();

                if (replaced) {
                    if (_linkUp) {
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
            public void reconnectingIn(int arg0) {
            }

            @Override
            public void reconnectionFailed(Exception arg0) {
            }

            @Override
            public void reconnectionSuccessful() {
            }

            private final XMPPConnection _c = _conn;
        });
    }

    @Override
    public synchronized void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        if (_conn == null || !_conn.isConnected()) {
            ps.println(indent + "disconnected");
        } else {
            ps.println(indent + "auth'ed " + _conn.isAuthenticated() +
                    ", secure " + _conn.isSecureConnection() +
                    ", tls " + _conn.isUsingTLS() +
                    ", compression: " + _conn.isUsingCompression());
        }
    }

    /**
     * Returns the credentials required to log into the XMPP server
     *
     * @return sha256(scrypt(p|u)|u)
     */
    public static String shaedXMPP()
    {
        if (s_shaedXmpp == null) {
            s_shaedXmpp = Base64.encodeBytes(SecUtil.hash(Cfg.scrypted(), XMPP_PASSWORD_SALT));
        }
        return s_shaedXmpp;
    }

    private volatile XMPPConnection _conn = null; // protect via this (volatile so that conn() can work properly)
    private volatile boolean _linkUp = false;

    private final String _resource;
    private final IXMPPServerConnectionWatcher _watcher;
    private final String _user = JabberID.did2user(Cfg.did());

    private static String s_shaedXmpp; // sha256(scrypt(p|u)|XMPP_PASSWORD_SALT)

    private static final Logger l = Loggers.getLogger(XMPPServerConnection.class);

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
}
