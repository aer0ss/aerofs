/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc. 2011.
 */

package com.aerofs.xray.server;

import com.aerofs.base.Loggers;
import com.aerofs.xray.server.core.BufferPool;
import com.aerofs.xray.server.core.Dispatcher;
import com.aerofs.xray.server.core.FatalIOEventHandlerException;
import com.aerofs.xray.server.core.IIOEventHandler;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.aerofs.xray.Constants.ZEPHYR_INVALID_CHAN_ID;
import static com.aerofs.xray.server.core.ZUtil.addInterest;
import static com.aerofs.xray.server.core.ZUtil.closeChannel;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.newHashMap;
import static java.nio.channels.SelectionKey.OP_ACCEPT;

/**
 * This is the boss IIoEventHandler. It creates PeerEndpoint objects and
 * registers them with the parent Dispatcher
 *
 * - default bytebuffer size should be configurable
 * - should have a way of setting the random seed
 * - look closer at NioProviderMetadata
 * - should Zephyr have the buffer pool? Maybe it should init with a reference to
 *   a buffer pool
 * - have to have timeouts so that I prune connections for which no data was sent
 * - find a way to avoid returning the selectionkey to arbitrary pes
 */
public class XRayServer implements IIOEventHandler
{
    public XRayServer(String host, short port, Dispatcher d)
    {
        _host = host;
        _port = port;
        _d = d;
        _nextid = 0;
        _idToKey = new ConcurrentHashMap<Integer, SelectionKey>();
        _bufpool = new BufferPool(32768, 1024);
        _srvsoc = null;
        _inited = false;
    }

    public void init() throws IOException
    {
        try {
            _d.init();

            InetSocketAddress isa = new InetSocketAddress(_host, _port);

            _srvsoc = ServerSocketChannel.open();
            _srvsoc.configureBlocking(false);
            _srvsoc.socket().setReuseAddress(true);
            _srvsoc.socket().setReceiveBufferSize(RECEIVE_BUFFER_SIZE);
            _srvsoc.socket().bind(isa);

            _d.register(_srvsoc, this, OP_ACCEPT);

            _inited = true;
        } catch (IOException e) {
            l.error("z: srv sock setup fail err:{}", e);
            stop();
            throw e;
        }
    }

    public void start()
    {
        _d.run();
    }

    //
    // implemented handlers
    //

    @Override
    public void handleAcceptReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {
        l.debug("z: acc hdl beg:{}", System.currentTimeMillis());

        checkState(_inited, "z: not inited");
        if (!k.isValid()) return;
        checkArgument(k.isAcceptable(), "z: k:" + k + ":not acceptable");

        SocketChannel sc;
        try {
            sc = _srvsoc.accept();
        } catch (IOException e) {
            String err = "z: srv:[" + _host + ":" + _port + "]:fail on acc";
            l.error("{}", err, e);
            stop();
            throw new FatalIOEventHandlerException(err, e);
        }

        // Rarely (one in a million or so) nio passes us a socket that isn't connected;
        // this trips the server as an NPE which we handle poorly. This attempts to let
        // zephyr continue in that circumstance.
        // (Note: getRemoteSocketAddress() returns null if socket is not connected)
        if (!sc.socket().isConnected()) {
            l.error("Server socket {}: can't accept non-connected socket", sc.socket().toString());
            stop();
            throw new FatalIOEventHandlerException("Accept passed non-connected socket");
        }

        String remaddr = sc.socket().getRemoteSocketAddress().toString();
        l.debug("z: conn from:{}", remaddr);

        int id = ZEPHYR_INVALID_CHAN_ID;
        try {
            sc.configureBlocking(false);
            sc.socket().setTcpNoDelay(true);
            sc.socket().setSoLinger(true, 0);
            sc.socket().setSendBufferSize(SEND_BUFFER_SIZE);
            sc.socket().setReceiveBufferSize(RECEIVE_BUFFER_SIZE);

            id = _nextid++;
            checkState(!_idToKey.containsKey(id), "z: id:" + id + " already used");

            PeerEndpoint ep = new PeerEndpoint(id, remaddr, this);
            SelectionKey pek = _d.register(sc, ep, 0); // hackish - but don't register for reads until we've sent the registration msg (hope this is OK!)

            l.info("z: create pe: map k:{} -> pe:{}", pek, ep.toString());

            // regardless of whether the message is sent back to the peer,
            // I want to put this id in the _idToKey map (so that this id is
            // not used again) this is because I don't know when the message
            // with this id is going to be sent out (if ever), but I don't want
            // to accidentally send two clients the same id
            SelectionKey prev = _idToKey.put(id, pek);
            checkState(prev == null, "z: id:" + id + ":not unique");

            ep.init(pek); // I'm really not a fan of doing this, because now they have access to the channel as well...
        } catch(ClosedChannelException e) {
            l.warn("z: sc:{}:fail reg with cce:{}", sc, e);

            _idToKey.remove(id);
            closeChannel(sc);
        } catch (IOException e) {
            l.warn("z: sc:{}:fail reg with ioe:{}", sc, e);

            _idToKey.remove(id);
            closeChannel(sc);
        }

        addInterest(k, OP_ACCEPT);

        l.debug("z: acc hdl fin:" + System.currentTimeMillis());
    }

    public void stop()
    {
        closeChannel(_srvsoc);

        Map<Integer, SelectionKey> idToKey = newHashMap(_idToKey);

        SelectionKey k = null;
        for (Map.Entry<Integer, SelectionKey> entry : idToKey.entrySet()) {
            try {
                k = entry.getValue();

                checkNotNull(k, "z: id:" + entry.getKey() + ":null key");
                checkState(k.attachment() != null, "z: id:" + entry.getKey() + ":null att");

                PeerEndpoint pe = (PeerEndpoint) k.attachment();
                pe.terminate("system-wide termination");
            } catch (Exception e) {
                l.warn("z: fail to terminate id:{}", entry.getKey());

                if (k != null) closeChannel(k.channel());
            }
        }

        _idToKey.clear();
        _d.shutdown();
    }

    //
    // utility
    //

    public void removeEndpoint(int id)
    {
        if (id == ZEPHYR_INVALID_CHAN_ID) return;

        SelectionKey k = _idToKey.remove(id);

        // cancelling the key is not strictly necessary since this is done
        // implicitly by the close
        if (k != null) {
            k.cancel();
        } else {
            // may also happen because we call removeEndpoint multiple times
            l.warn("z: id:{}:null k", id);
            return;
        }

        if (k.channel() != null) {
            closeChannel(k.channel());
        } else {
            l.warn("z: id:{}:null ch", id);
        }
    }

    public SelectionKey getSelectionKey(int id)
    {
        SelectionKey k = _idToKey.get(id);
        if (k != null && !k.isValid()) k = null;
        return k;
    }

    /**
     * Returns the attachment of an {@link SelectionKey} cast into a
     * {@link PeerEndpoint} if the PeerEndpoint exists and is valid
     *
     * @param id id of the from which to get the data
     * @return a non-null PeerEndpoint object
     * @throws ExInvalidPeerEndpoint if the PeerEndpoint is invalid or doesn't exist
     */
    public PeerEndpoint getPeerEndpoint(int id)
        throws ExInvalidPeerEndpoint
    {
        SelectionKey k = getSelectionKey(id);
        if (k == null) throw new ExInvalidPeerEndpoint(id);

        PeerEndpoint pe = (PeerEndpoint) k.attachment();
        checkArgument(pe != null, "k:" + k + ": null att");
        return pe;
    }

    /**
     * @return a valid byte buffer of the default allocation size
     */
    public ByteBuffer getBuffer()
    {
       return _bufpool.getBuffer_();
    }

    public void putBuffer(ByteBuffer b)
    {
       _bufpool.putBuffer_(b);
    }

    //
    // unimplemented handler methods
    //

    @Override
    public void handleConnectReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {}

    @Override
    public void handleReadReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {}

    @Override
    public void handleWriteReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {}

    @Override
    public void handleKeyCancelled_(SelectionKey k)
        throws FatalIOEventHandlerException
    {}

    //
    // members
    //

    private final String _host;
    private final short _port;
    private final Dispatcher _d;
    private int _nextid;
    private final Map<Integer, SelectionKey> _idToKey;
    private final BufferPool _bufpool;
    private ServerSocketChannel _srvsoc;
    private boolean _inited;

    private static final Logger l = Loggers.getLogger(XRayServer.class);

    private static final int RECEIVE_BUFFER_SIZE = 262144;
    private static final int SEND_BUFFER_SIZE = RECEIVE_BUFFER_SIZE;
}
