/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp;

import com.aerofs.daemon.core.net.link.ILinkStateListener;
import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.IDebug;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.tng.base.http.ProxyAwareSocketFactory;
import com.aerofs.lib.Param;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.DID;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.Builder;
import com.google.common.collect.ImmutableSet;
import org.apache.log4j.Logger;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence.Mode;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

final class XMPPServerConnectionService implements ILinkStateListener, IStartable, IDebug
{
    private static final Logger l = Util.l(XMPPServerConnectionService.class);

    private final String _user;
    private final String _resource;
    private final Proxy _proxy;
    private final AtomicBoolean _started = new AtomicBoolean(false);
    private final Set<IXMPPServerConnectionListener> _listeners = new HashSet<IXMPPServerConnectionListener>();

    private volatile boolean _linkUp = false;

    @Nullable private volatile XMPPConnection _conn = null; // protect via this (volatile so that conn_() can work properly)

    static {
        XMPPConnection.DEBUG_ENABLED = false;

        // workaround for NPE during auth - see http://www.igniterealtime.org/community/thread/35976
        SASLAuthentication.supportSASLMechanism("PLAIN", 0);
    }

    static XMPPServerConnectionService getInstance_(
            ILinkStateService networkLinkStateService, DID did, Proxy proxy)
    {
        XMPPServerConnectionService xmppconn = new XMPPServerConnectionService(ID.did2user(did),
                ID.resource(false), proxy);
        networkLinkStateService.addListener_(xmppconn, sameThreadExecutor());
        return xmppconn;
    }

    private XMPPServerConnectionService(String user, String resource, Proxy proxy)
    {
        this._user = user;
        this._resource = resource;
        this._proxy = proxy;
    }

    @Override
    public void start_()
    {
        boolean previouslyStarted = _started.getAndSet(true);
        if (previouslyStarted) return;

        startConnect(false);

        l.info("started");
    }

    @Override
    public void onLinkStateChanged_(ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed, ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> previous)
    {
        // FIXME: I'm sure there was a bug ...[sigh] such confusing naming (was XMPP.allLinksDown)
        boolean linkUp = !current.isEmpty();

        boolean wasup = _linkUp;
        _linkUp = linkUp;

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
            // can't set _conn to null if !linkUp because disconnection handler won't run
        }

        if (!linkUp && c != null && connected) {
            try {
                c.disconnect();
            } catch (NullPointerException e) {
                l.warn("smk npe");
            }
        } else if (!wasup && linkUp) {
            startConnect(false);
        }
    }

    public void addListener_(IXMPPServerConnectionListener l)
    {
        assert !_started.get();
        assert !_listeners.contains(l);

        _listeners.add(l);
    }

    //--------------------------------------------------------------------------
    //
    //
    // XMPPServerConnectionService methods
    //
    //
    //--------------------------------------------------------------------------

    /**
     * @throws XMPPException if not connected to the server IMPORTANT: even if you get back a conn_
     * it may be invalid
     */
    private XMPPConnection getConn_()
            throws XMPPException
    {
        XMPPConnection c = _conn;
        if (c == null) throw new XMPPException("disconnected");
        return c;
    }

    // FIXME (AG): does this actually have to be synchronized?
    // _conn is volatile, so the reference is either valid or invalid (null). If it's valid,
    // we should be able to call operations on it. sendPacket enqueues a packet onto Smack's
    // internal "outgoing packet queue" where it's picked by by a thread for future processing
    // This means that multiple threads can call sendPacket simultaneously.
    synchronized void sendMessage(Message msg)
            throws XMPPException
    {
        try {
            getConn_().sendPacket(msg);
        } catch (IllegalStateException e) {
            // NOTE: this can happen because smack considers it illegal to attempt to send a packet
            // if the channel is not connected. Since we may be notified of a disconnection after
            // enqueing a packet to be sent, it's entirely possible for this to occur
            throw new XMPPException(e);
        }
    }

    synchronized void leaveRoom(MultiUserChat muc)
    {
        muc.changeAvailabilityStatus("leaving", Mode.xa);
        muc.leave();
    }

    private void joinRoom_(String user, MultiUserChat muc)
            throws XMPPException
    {
        l.info("joining " + muc.getRoom());

        // requesting no history
        DiscussionHistory history = new DiscussionHistory();
        history.setMaxChars(0);

        try {
            muc.join(user, null, history, SmackConfiguration.getPacketReplyTimeout());
        } catch (IllegalStateException e) {
            throw new XMPPException(e);
        }

        l.info("room joined");
    }

    private void createRoom_(MultiUserChat muc)
            throws XMPPException
    {
        l.info("creating " + muc.getRoom());

        try {
            muc.create(ID.did2user(Cfg.did()));

            // create_ an instant room using the server's default configuration
            // see: http://www.igniterealtime.org/builds/smack/docs/latest/documentation/extensions/muc.html
            // the xmpp server should be set up with the following defaults:
            // muc#roomconfig_enablelogging: false
            // muc#roomconfig_persistentroom: true
            // muc#roomconfig_maxusers: unlimited
            muc.sendConfigurationForm(new Form(Form.TYPE_SUBMIT));
        } catch (IllegalStateException e) {
            throw new XMPPException(e);
        }

        l.info("room created");
    }

    /**
     * Join the MUC room if it exists; if it doesn't exist, make the MUC and then join the room
     */
    synchronized MultiUserChat makeAndJoinMUC(String user, String name)
            throws XMPPException
    {
        MultiUserChat muc = new MultiUserChat(getConn_(), name);
        joinRoom_(user, muc);
        try {
            l.info("gri:" + name);
            MultiUserChat.getRoomInfo(getConn_(), name);
        } catch (XMPPException e) {
            if (e.getXMPPError() != null && e.getXMPPError().getCode() == 404) {
                l.info("muc " + name + " not exists. create_ now.");
                createRoom_(muc);
            } else {
                l.error(e.getMessage());
                throw e;
            }
        }

        joinRoom_(user, muc);

        return muc;
    }

    /**
     * Thread-safe
     *
     * @return a new SMACK connection
     */
    private XMPPConnection createNewConnection()
    {
        // The xmpp server address is an unresolved hostname.
        // We avoid resolving the hostname ourselves and let
        // SMACK do the DNS query on its thread.
        InetSocketAddress address = Param.xmppAddress();
        ConnectionConfiguration cc = new ConnectionConfiguration(
                address.getHostName(), address.getPort());
        cc.setServiceName(DaemonParam.XMPP.SERVER_DOMAIN);
        cc.setSecurityMode(SecurityMode.required);
        cc.setSelfSignedCertificateEnabled(true);
        cc.setVerifyChainEnabled(false);
        cc.setVerifyRootCAEnabled(false);
        cc.setReconnectionAllowed(false);
        cc.setCompressionEnabled(true);
        cc.setSocketFactory(new ProxyAwareSocketFactory(_proxy));

        return new XMPPConnection(cc);
    }

    private void startConnect(final boolean initialDelay)
    {
        // TODO use scheduler instead of threads?
        Util.exponentialRetryNewThread("xsct", new Callable<Void>()
        {
            @Override
            public Void call()
                    throws Exception
            {
                if (initialDelay) {
                    l.info("reconnect in " + Param.EXP_RETRY_MIN_DEFAULT);
                    Util.sleepUninterruptable(Param.EXP_RETRY_MIN_DEFAULT);
                }

                try {
                    if (!_linkUp) {
                        l.info("link down. do not attempt connect.");
                        return null;
                    }
                    synchronized (XMPPServerConnectionService.this) {
                        connect_();
                    } // FIXME: this does not seem right
                } catch (XMPPException e) {
                    // 502: remote-server-error(502): java.net.ConnectException: Operation timed out
                    // 504: remote-server-error(504): connection refused
                    if (e.getXMPPError() != null) {
                        int errcode = e.getXMPPError().getCode();
                        if (errcode == 502 || errcode == 504) {
                            throw new Exception(String.valueOf(errcode));
                        }
                    }
                    throw new Exception(Util.e(e));
                } catch (Exception e) {
                    l.error(Util.stackTrace2string(e));
                }

                return null;
            }
        }, Exception.class);
    }

    private void connect_()
            throws XMPPException
    {
        try {
            connectImpl_();
        } catch (IllegalStateException e) {
            throw new XMPPException(e);
        }
    }

    // should be accessed by only one thread at a time. we don't want to mess up
    // with the xmpp library. and access to _conn need synchronization, too.
    private void connectImpl_()
            throws XMPPException
    {
        if (_conn != null && _conn.isAuthenticated()) {
            l.warn("beginning processIncoming_ to replace old connection");
        }

        l.info("connecting");

        XMPPConnection c = createNewConnection();
        c.connect();

        l.info("connected. logging in");

        c.login(_user, ID.getShaedXMPP(), _resource);

        l.info("logged in");
        _conn = c; // this is the point at which changes are visible

        // I would prefer to only set _conn _after_ calling _listener.connected, but apparently
        // Multicast.java uses conn_() internally... // FIXME: this doesn't seem right as well...

        // FIXME: we're calling alien code inline in a synchronized block below

        try {
            for (IXMPPServerConnectionListener listener : _listeners) {
                l.info("notifying listeners that xmpp has connected l:" + listener);
                listener.xmppServerConnected(c);
            }
        } catch (XMPPException e) {
            l.warn("caught exception while adding listener err:" + e);
            _conn = null;
            throw e;
        } catch (RuntimeException e) {
            l.warn("caught error while adding listener err:" + e);
            _conn = null;
            throw e;
        }

        // FIXME: we need to verify that the connection is actually valid prior to adding a listener
        // we don't rely on Smack API's connect_ capability as experiments
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
                assert _c != null : "null listener conn_";

                boolean replaced = false;
                synchronized (XMPPServerConnectionService.this) { // FIXME: remove!
                    if (_conn == _c) {
                        try {
                            l.info("notifying listeners of disconnection");
                            for (IXMPPServerConnectionListener l : _listeners) { // FIXME: again, calling alien code inline in a synchronized block - the right thing to do is ask for futures and block on them
                                l.xmppServerDisconnected();
                            }
                        } finally {
                            _conn = null;
                            replaced = true;
                            l.info("conn_ replaced");
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

            private final XMPPConnection _c = _conn;
        });
    }

    //--------------------------------------------------------------------------
    //
    //
    // IDebug methods
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public void dumpStat(PBDumpStat template, Builder bd)
            throws Exception
    {
        // noop for us
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

    //
    // types
    //

    /**
     * <strong>IMPORTANT:</strong> all methods in this interface are called from within this
     * object's thread. Ensure that any methods/state that are called or modified during these
     * method calls are thread-safe
     */
    static interface IXMPPServerConnectionListener
    {
        void xmppServerConnected(XMPPConnection conn)
                throws XMPPException;

        void xmppServerDisconnected();
    }

    //
    // members
    //


}
