package com.aerofs.ssmp;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.ssmp.SSMPClient.ConnectionListener;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.base.config.ConfigurationProperties.getAddressProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * Auto-reconnecting SSMP client with more granular event dispatching
 */
public class SSMPConnection implements ConnectionListener, EventHandler {
    private final static Logger l = Loggers.getLogger(SSMPConnection.class);

    private final Timer _timer;
    private final SSMPClient _client;

    private long _delay = 0;

    private final SSMPIdentifier _login;
    private String _secret;

    private final AtomicBoolean _stopped = new AtomicBoolean(true);
    private final AtomicBoolean _loggedIn = new AtomicBoolean();

    private final List<ConnectionListener> _connectionListeners = new ArrayList<>();

    private final TrieNode<EventHandler> _ucastHandlers = new TrieNode<>();
    private final TrieNode<EventHandler> _bcastHandlers = new TrieNode<>();
    private final Map<String, EventHandler> _mcastHandlers = new HashMap<>();

    private final List<EventHandler> _eventHandlers = new ArrayList<>();

    public SSMPConnection(String secret, InetSocketAddress serverAddress, Timer timer,
                          ChannelFactory channelFactory, SslHandlerFactory sslHandlerFactory) {
        this(SSMPIdentifier.ANONYMOUS, serverAddress, timer, channelFactory, sslHandlerFactory);
        _secret = secret;
    }
    public SSMPConnection(DID did, InetSocketAddress serverAddress, Timer timer,
                          ChannelFactory channelFactory, SslHandlerFactory sslHandlerFactory) {
        this(SSMPIdentifier.fromInternal(did.toStringFormal()), serverAddress, timer, channelFactory, sslHandlerFactory);
    }

    public SSMPConnection(SSMPIdentifier login, InetSocketAddress serverAddress, Timer timer,
                          ChannelFactory channelFactory, SslHandlerFactory sslHandlerFactory) {
        _timer = timer;
        _login = login;
        _client = new SSMPClient(serverAddress, timer, channelFactory, sslHandlerFactory, this);
    }

    public boolean isLoggedIn() {
        return _loggedIn.get();
    }

    public void addConnectionListener(ConnectionListener l) {
        _connectionListeners.add(l);
    }

    public void addEventHandler(EventHandler h) {
        _eventHandlers.add(h);
    }

    public void addUcastHandler(String prefix, EventHandler h) {
        _ucastHandlers.addChild(prefix, h);
    }

    public void addMcastHandler(String topic, EventHandler h) {
        _mcastHandlers.put(topic, h);
    }

    public void addBcastHandler(String prefix, EventHandler h) {
        _bcastHandlers.addChild(prefix, h);
    }

    public void start() {
        if (_stopped.compareAndSet(true, false)) {
            connect();
        }
    }

    public void stop() {
        if (_stopped.compareAndSet(false, true)) {
            _client.disconnect();
        }
    }

    public ListenableFuture<SSMPResponse> request(SSMPRequest request) {
        return _client.request(request);
    }

    private void connect() {
        if (_login.isAnonymous()) {
            _client.connect(_login, SSMPIdentifier.fromInternal("secret"), _secret, this);
        } else {
            _client.connect(_login, SSMPIdentifier.fromInternal("cert"), "", this);
        }
    }

    private void reconnect() {
        _loggedIn.set(false);
        if (_stopped.get()) {
            l.info("stopped");
            return;
        }
        l.info("reconnect in {}", _delay);
        _timer.newTimeout(timeout -> connect(),_delay, TimeUnit.MILLISECONDS);
        _delay = Math.min(Math.max(_delay * 2, 100), 60000);
    }

    @Override
    public void connected() {
        try {
            l.info("logged in");
            _loggedIn.set(true);
            _delay = 0;
            _connectionListeners.forEach(ConnectionListener::connected);
        } catch (Exception e) {
            l.warn("login failed", e);
        }
    }

    @Override
    public void disconnected() {
        _connectionListeners.forEach(ConnectionListener::disconnected);
        reconnect();
    }

    @Override
    public void eventReceived(SSMPEvent ev) {
        l.debug("recv event {} {} {} {}", ev.from, ev.type, ev.to,
                ev.payload != null ? new String(ev.payload, StandardCharsets.UTF_8) : null);
        EventHandler h = null;
        switch (ev.type) {
        case UCAST:
            h = routeByPayload(new String(ev.payload, StandardCharsets.UTF_8), _ucastHandlers);
            break;
        case MCAST:
            h = _mcastHandlers.get(ev.to.toString());
            break;
        case BCAST:
            h = routeByPayload(new String(ev.payload, StandardCharsets.UTF_8), _bcastHandlers);
            break;
        default:
            break;
        }
        if (h != null) h.eventReceived(ev);

        _eventHandlers.forEach(l -> l.eventReceived(ev));
    }

    private static EventHandler routeByPayload(String payload, TrieNode<EventHandler> root) {
        int idx = payload != null ? payload.indexOf(' ') : -1;
        return idx > 0 ? root.get(payload.substring(0, idx)) : null;
    }

    public static InetSocketAddress getServerAddressFromConfiguration()
    {
        String baseHost = getStringProperty("base.host.unified");

        return getAddressProperty("base.ssmp.address",
                InetSocketAddress.createUnresolved(baseHost, 29438));
    }
}
