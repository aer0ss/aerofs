/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc. 2011.
 */

package com.aerofs.zephyr.server;

import com.aerofs.lib.Util;
import com.aerofs.zephyr.core.BufferPool;
import com.aerofs.zephyr.core.Dispatcher;
import com.aerofs.zephyr.core.FatalIOEventHandlerException;
import com.aerofs.zephyr.core.IIOEventHandler;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.aerofs.zephyr.Constants.ZEPHYR_INVALID_CHAN_ID;
import static com.aerofs.zephyr.Constants.ZEPHYR_REG_MSG_LEN;
import static com.aerofs.zephyr.core.ZUtil.addInterest;
import static com.aerofs.zephyr.core.ZUtil.closeChannel;

/**
 * This is the boss IIoEventHandler. It creates PeerEndpoint objects and
 * registers them with the parent Dispatcher
 *
 * - default bytebuffer size should be configurable
 * - should have a way of setting the random seed
 * - look closer at NioProviderMetadata
 * - should Zephyr have the buffer pool? Maybe it should init_ with a reference to
 *   a buffer pool
 * - have to have timeouts so that I prune connections for which no data was sent
 * - find a way to avoid returning the selectionkey to arbitrary pes
 */
public class ZephyrServer implements IIOEventHandler
{
    public ZephyrServer(String host, short port, Dispatcher d)
    {
        _host = host;
        _port = port;
        _d = d;
        _nextid = 0;
        _idToKey = new ConcurrentHashMap<Integer, SelectionKey>();
        _bufpool = new BufferPool(32768, 1024);
        _ssc = null;
        _inited = false;
    }

    public void init() throws IOException
    {
        InetSocketAddress isa = new InetSocketAddress(_host, _port);

        try {
            _ssc = ServerSocketChannel.open();
            _ssc.configureBlocking(false);
            _ssc.socket().bind(isa);

            _d.register(_ssc, this, SelectionKey.OP_ACCEPT);

            _inited = true;
        } catch (IOException e) {
            l.error("z: srv sock setup fail:" + e);
            terminate();
            throw e;
        }
    }

    //
    // implemented handlers
    //

    @Override
    public void handleAcceptReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {
        assert _inited : "z: not inited";

        l.info("z: acc hdl beg:" + System.currentTimeMillis());

        if (!k.isValid()) return;

        assert k.isAcceptable() : ("z: k:" + k + ":not acceptable");

        // do I have to assert that I am the channel for this key?

        SocketChannel sc;
        try {
            sc = _ssc.accept();
        } catch (IOException e) {
            String err = "z: srv:[" + _host + ":" + _port + "]:fail on acc";
            l.fatal(err + ":" + e);
            terminate();
            throw new FatalIOEventHandlerException(err, e);
        }

        l.info("z: conn from:" + sc.socket().getRemoteSocketAddress().toString());

        int id = ZEPHYR_INVALID_CHAN_ID;
        try {
            sc.configureBlocking(false);
            sc.socket().setTcpNoDelay(true);
            sc.socket().setSoLinger(true, 0);

            id = _nextid++;
            assert !_idToKey.containsKey(id) : ("z: id:" + id + " already used");

            PeerEndpoint ep = new PeerEndpoint(id, this);
            SelectionKey pek = _d.register(sc, ep, SelectionKey.OP_READ);

            // regardless of whether the message is sent back to the peer,
            // I want to put this id in the _idToKey map (so that this id is
            // not used again) this is because I don't know when the message
            // with this id is going to be sent out (if ever), but I don't want
            // to accidentally send two clients the same id
            SelectionKey prev = _idToKey.put(id, pek);
            assert prev == null : ("z: id:" + id + ":not unique");

            ep.init(pek); // I'm really not a fan of doing this, because now they have access to the channel as well...
        } catch(ClosedChannelException e) {
            l.warn("z: sc:" + sc + ":reg fail with sel:" + e);

            _idToKey.remove(id);
            closeChannel(sc);
        } catch (IOException e) {
            l.warn("z: sc:" + sc + ":fail to set nb" + e);

            _idToKey.remove(id);
            closeChannel(sc);
        }

        addInterest(k, SelectionKey.OP_ACCEPT);

        l.info("z: acc hdl fin:" + System.currentTimeMillis());
    }

    public void terminate()
    {
        closeChannel(_ssc);

        Map<Integer, SelectionKey> idToKey =
            new HashMap<Integer, SelectionKey>(_idToKey);

        SelectionKey k = null;
        for (Map.Entry<Integer, SelectionKey> entry : idToKey.entrySet()) {
            try {
                k = entry.getValue();

                assert k != null : ("z: id:" + entry.getKey() + ":null key");
                assert k.attachment() != null :
                    ("z: id:" + entry.getKey() + ":null att");

                PeerEndpoint pe = (PeerEndpoint)k.attachment();
                pe.terminate();
            } catch (Exception e) {
                l.warn("z: fail to terminate id:" + entry.getKey());

                if (k != null) closeChannel(k.channel());
            }
        }

        _idToKey.clear();
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
            l.warn("z: id:" + id + ":null k");
            return;
        }

        if (k.channel() != null) {
            closeChannel(k.channel());
        } else {
            l.warn("z: id:" + id + ":null ch");
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
        assert pe != null : ("k:" + k + ": null att");
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
    private Dispatcher _d;
    private int _nextid;
    private Map<Integer, SelectionKey> _idToKey;
    private BufferPool _bufpool;
    private ServerSocketChannel _ssc;
    private boolean _inited;
    private Logger l = Util.l(ZephyrServer.class);
}
