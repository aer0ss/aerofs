/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc. 2011.
 */

package com.aerofs.zephyr.server;

import com.aerofs.lib.Util;
import com.aerofs.zephyr.core.FatalIOEventHandlerException;
import com.aerofs.zephyr.core.IIOEventHandler;
import com.aerofs.zephyr.core.ExAlreadyBound;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import static com.aerofs.zephyr.Constants.ZEPHYR_BIND_MSG_LEN;
import static com.aerofs.zephyr.Constants.ZEPHYR_INVALID_CHAN_ID;
import static com.aerofs.zephyr.Constants.ZEPHYR_MAGIC;
import static com.aerofs.zephyr.Constants.ZEPHYR_MSG_BYTE_ORDER;
import static com.aerofs.zephyr.core.ZUtil.addInterest;
import static com.aerofs.zephyr.core.ZUtil.getSocketChannel;
import static com.aerofs.zephyr.server.ServerConstants.EndpointState.BOUND;
import static com.aerofs.zephyr.server.ServerConstants.EndpointState.CONNECTED;
import static com.aerofs.zephyr.server.ServerConstants.EndpointState.REGISTERED;
import static com.aerofs.zephyr.server.ServerConstants.ReadStatus.EOF;
import static com.aerofs.zephyr.server.ServerConstants.ReadStatus.HAS_BYTES;
import static com.aerofs.zephyr.server.ServerConstants.ReadStatus.NO_BYTES;

/**
 * - find a way to avoid looking up the id in the map every single time
 * - need setstate() function
 */
public class PeerEndpoint implements IIOEventHandler
{
    public PeerEndpoint(int id, ZephyrServer boss)
    {
        assert id != ZEPHYR_INVALID_CHAN_ID : ("id:" + id + ":invalid");

        _boss = boss;
        _ourid = id;
        _remid = ZEPHYR_INVALID_CHAN_ID;
        _rdbuf = null;
        _wrbuf = null;
        _state = CONNECTED;

        l.info("created: " + compact());
    }

    public void init(SelectionKey k)
        throws IOException
    {
        assert _state == CONNECTED :
            (compact() + ": state:" + _state + ":invalid for init");
        assert _wrbuf == null : (compact() + ": buf not null");

        ByteBuffer reg = _boss.getBuffer();
        reg = createNewConnectionResponse(reg, _ourid);
        reg.flip();
        try {
            boolean written = write(k, reg);
            if (written) {
                l.info(compact() + ": reg comp'd id:" + _ourid + " inited");
                _state = REGISTERED;
            }
        } catch (IOException e) {
            l.warn(compact() + ": err on req resp wr");
            terminate();
            throw e;
        }
    }

    @Override
    public void handleWriteReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {
        if (!k.isValid()) return;

        assert k.isWritable() : (compact() + ": k:" + k + ":not writable");
        assert _wrbuf != null : (compact() + ": null pending buffer");

        try {
            // we are switching out the _wrbuf so that the assertion in write()
            // can succeed. it's an open question whether write() should be changed
            ByteBuffer b = _wrbuf;
            _wrbuf = null;

            boolean written = write(k, b);
            if (written) {
                // notify the source of the pending packet that the write was
                // completed. this _does_ not happen in the CONNECTED state, since
                // in this state we haven't set the other leg's id yet
                if (_remid != ZEPHYR_INVALID_CHAN_ID) {
                    assert _state != CONNECTED :
                        (compact() + ": invalid state for wr notification");

                    PeerEndpoint spe = _boss.getPeerEndpoint(_remid);
                    spe.dstWriteCompleted();
                }

                // we will not be writing out anything _but_ the registration
                // message if we're in the connected state (i.e. we don't have
                // to worry that another endpoint was writing to us). therefore
                // it is safe for us to transition to the registered state
                if (_state == CONNECTED) {
                    _state = REGISTERED;
                    l.info(compact() + ":reg wr comp'd id:" + _ourid + " inited");
                }
            }
        } catch (ExInvalidPeerEndpoint e) {
            l.warn(compact() + ": src:" + _remid + " not exist");
            e.printStackTrace();
            terminate();
        } catch (IOException e) {
            l.warn(compact() + ": err on wr:" + e);
            e.printStackTrace();
            terminate();
        }
    }

    @Override
    public void handleReadReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {
        if (!k.isValid()) return;

        assert k.isReadable() : (compact() + ": k:" + k + ":not readable");

        try {
            switch (_state) {
                case REGISTERED:
                    processInRegisteredState(k);
                    break;
                case BOUND:
                    processInBoundState(k);
                    break;
                default:
                    assert false :
                        (compact() + ": state:" + _state + ":unexpected state for rd");
            }
        } catch (Exception e) {
            l.warn(compact() + ": err on rd:" + e);
            e.printStackTrace();
            terminate();
        }
    }

    private void processInRegisteredState(SelectionKey k)
    {
        assert _state == REGISTERED :
            (compact() + ": state:" + _state + ":invalid for func");

        SocketChannel ch = getSocketChannel(k);
        ByteBuffer b = (_rdbuf == null ? _boss.getBuffer() : _rdbuf);
        b.limit(ZEPHYR_BIND_MSG_LEN); // idempotent // FIXME: I should only read in len, and then bytes to be fwd compatible
        try {
            if (read(k, ch, b) != HAS_BYTES) {
                return;
            }

            // check for incomplete reads

            if (b.position() < ZEPHYR_BIND_MSG_LEN) {
                l.warn(compact() + ": too few read bytes");

                assert _rdbuf == null || (b == _rdbuf) :
                    (compact() + ": mismatched rd buf");

                addInterest(k, SelectionKey.OP_READ);
                _rdbuf = b;
                b = null; // use to prevent b being freed
                return;
            }

            _rdbuf = null;

            // check the message format and parameters

            b.flip();
            byte[] magic = new byte[ZEPHYR_MAGIC.length];
            b.get(magic);
            if (!Arrays.equals(magic, ZEPHYR_MAGIC)) {
                l.warn(compact() + ": unexpected message");
                terminate();
                return;
            }

            b.getInt(); // ignore length value; simply use to move the position

            int dstid = b.getInt();
            try {
                PeerEndpoint dpe = _boss.getPeerEndpoint(dstid);
                dpe.bind(_ourid);
            } catch (ExInvalidPeerEndpoint e) {
                l.warn(compact() + ": no peer id:" + dstid);
                terminate();
                return;
            } catch (ExAlreadyBound e) {
                l.warn(compact() + ": peer already bound:" + e);
                terminate();
                return;
            }

            // bind (from our side)

            addInterest(k, SelectionKey.OP_READ);
            _remid = dstid;
            _state = BOUND;
        } catch (IOException e) {
            l.warn(compact() + ": err on rd:" + e);
            _rdbuf = null; // keep this here...ensures that putBuffer_ only ever called once
            terminate();
        } finally {
            if (b != null) _boss.putBuffer(b);
        }
    }

    private void processInBoundState(SelectionKey k)
    {
        assert _state == BOUND :
            (compact() + ":state:" + _state + ":invalid for func");

        SocketChannel ch = getSocketChannel(k);

        // ----

        // needs to be synchronized wrt. dest because in the interval between
        // hasPendingWrite and write someone else may write
        // is this even possible? I don't think so, since there should only ever
        // be a one-to-one mapping between ourid and _remid

        // this is just asking for trouble...splitting up hasPendingWrite and write
        // and doing this passedOwnership thing

        PeerEndpoint dpe = null;
        try {
            // how is this going to work if our channel and the dest channel are
            // handled by different selectors on different threads?
            dpe = _boss.getPeerEndpoint(_remid);
        } catch (ExInvalidPeerEndpoint e) {
             l.warn(compact() + ": invalid dest:" + _remid);
            terminate();
            return;
        }

        if (dpe.hasPendingWrite())
        {
            // do not read! this will get the sender's flow control working...
            // assumes that we are level triggered
            l.warn(compact() + ": cannot attempt wr to dst:" + _remid);
            return;
        }

        boolean passedOwnership = false;
        ByteBuffer b = _boss.getBuffer();
        try {
            // read from our side
            ServerConstants.ReadStatus status = read(k, ch, b);
            if (status != HAS_BYTES) {
                return;
            }

            // find a way to avoid doing this...
            SelectionKey dkey = _boss.getSelectionKey(_remid);
            assert dkey != null : (compact() + ": null key for dst:" + _remid);

            // write to destination
            b.flip();
            passedOwnership = true;
            dpe.write(dkey, b);

            addInterest(k, SelectionKey.OP_READ);
        } catch (IOException e) {
            l.warn(compact() + ": err in data tx:" + e);
            if (!passedOwnership) _boss.putBuffer(b);
            terminate();
        }

        // ----
    }

    /**
     * @return whether an {@link ByteBuffer} is already buffered for write
     */
    public boolean hasPendingWrite()
    {
        return _wrbuf != null;
    }

    /**
     * @param id channel id to which to bind this PeerEndpoint object to
     * @throws ExAlreadyBound if the channel is already bound to any other zid
     */
    public void bind(int id)
        throws ExAlreadyBound
    {
        assert id != ZEPHYR_INVALID_CHAN_ID :
            (compact() + ": invalid id:" + id + " for bind");

        // should this be an assert instead?
        if (_remid != ZEPHYR_INVALID_CHAN_ID && _remid != id) {
            throw new ExAlreadyBound(_remid, id);
        }

        _remid = id;
    }

    /**
     * Cleans up resources allocated by this PeerEndpoint object and also
     * calls terminate() on the destination PeerEndpoint object. Also removes
     * this PeerEndpoint from the Zephyr server.
     */
    public void terminate()
    {
        //
        // clean ourself up
        //

        if (_rdbuf != null) {
            _boss.putBuffer(_rdbuf);
            _rdbuf = null;
        }

        if (_wrbuf != null) {
            _boss.putBuffer(_wrbuf);
            _wrbuf = null;
        }

        _boss.removeEndpoint(_ourid);

        //
        // now cleanup our destination
        //

        try {
            PeerEndpoint dpe = _boss.getPeerEndpoint(_remid);
            dpe.terminate();
        } catch (ExInvalidPeerEndpoint e) {
            // ignore
        }
    }

    /**
     * @return full string representation of our object
     */
    public String toString()
    {
        return "pe:" + _ourid + " dst:" + _remid + " state:" + _state +
            (_wrbuf == null ? "null" : "buf[0:" + _wrbuf.limit() + "]");
    }

    //
    // local utility
    //

    /**
     * Called by the destination PeerEndpoint object when it has finished writing
     * a block that we buffered at some time in the past
     */
    private void dstWriteCompleted()
    {
        SelectionKey k = _boss.getSelectionKey(_ourid);
        assert k != null : (compact() + ": null self key");

        if (!k.isValid()) return;

        addInterest(k, SelectionKey.OP_READ);
    }

    /**
     * Writes a buffer if possible to this PeerEndpoint object's channel. Stores
     * the passed-in {@link ByteBuffer} as a pending buffer if it could not write
     * all bytes in one shot. Also verifies that there are no pending writes
     * already.
     *
     * @important by calling this function you <strong>pass ownership</strong>
     * of the buffer <code>b</code>
     *
     * @param k {@link SelectionKey} this PeerEndpoint object's key
     * (for re-registering interest)
     * @param b ByteBuffer into which to read bytes
     * @return whether all the bytes were written or not
     *
     * @throws IOException on any write exception
     */
    private boolean write(SelectionKey k, ByteBuffer b)
        throws IOException
    {
        assert _wrbuf == null : (compact() + ": pending write");

        // never buffer more than one byte - since both legs are TCP, if
        // there are pending writes we will stop reading and let the underlying
        // transport flow-control mechanisms take over
        boolean written = false;
        SocketChannel ch = getSocketChannel(k);
        try {
            ch.write(b);
            if (b.hasRemaining()) {
                l.info(compact() + ": wr pending");
                addInterest(k, SelectionKey.OP_WRITE);
                _wrbuf = b;
            } else {
                _boss.putBuffer(b);
                written = true;
            }
        } catch (IOException e) {
            l.warn(compact() + ": ch:" + ch + " err on wr:" + e);
            _boss.putBuffer(b);
            throw e;
        }

        return written;
    }

    /**
     * Performs a read() on a {@link SocketChannel}. Calls terminate() if the
     * channel returns EOF, and reregisters for interest if no bytes were available
     * on this read. Otherwise, the passed-in {@link ByteBuffer} is populated with
     * the read bytes
     *
     * @important caller is responsible for allocating and releasing buffer b
     * <code>b</code> should be released if ReadStatus.EOF or
     * ReadStatus.NO_BYTES are returned. Also, clear() is
     * <strong>not called</strong> on b before it is used
     *
     * @param k this PeerEndpoint object's {@link SelectionKey} (for re-registering
     * interest)
     * @param ch SocketChannel from which to read
     * @param b ByteBuffer to populate with read data
     * @return an enum signalling how many bytes (if any) were read
     * @throws IOException on any read exception
     */
    private ServerConstants.ReadStatus read(SelectionKey k, SocketChannel ch, ByteBuffer b)
        throws IOException
    {
        ServerConstants.ReadStatus status = NO_BYTES;

        int rd = ch.read(b);

        switch (rd) {
            case -1:
                status = EOF;
                terminate();
                break;
            case 0:
                status = NO_BYTES;
                addInterest(k, SelectionKey.OP_READ);
                break;
            default:
                assert rd > 0 : (compact() + ": bad bytes rd:" + rd);
                status = HAS_BYTES;
        }

        return status;
    }

    /**
     * Populates a {@link ByteBuffer} with a NewConnectionResponse message
     * @important does not clear() or flip() the ByteBuffer prior to, or after use
     *
     * @param b ByteBuffer to populate
     * @param id connection id
     * @return the same ByteBuffer passed in
     */
    private static ByteBuffer createNewConnectionResponse(ByteBuffer b, int id)
    {
        b.order(ZEPHYR_MSG_BYTE_ORDER);

        b.put(ZEPHYR_MAGIC); // magic number id'ing zephyr messages
        b.putInt(0); // length placeholder
        int lenpos = b.position(); // index after the length
        b.putInt(id); // server-assigned connection id

        int len = b.position(); // find total bytebuffer size
        b.position(ZEPHYR_MAGIC.length); // go to the spot right after the magic
        b.putInt(len - lenpos); // write the msg-len
        b.position(len); // go back to the end
        return b;
    }

    /**
     * @return compact representation of this PeerEndpoint
     */
    private String compact()
    {
        return "pe:" + _ourid;
    }

    //
    // unimplemented handlers
    //

    @Override
    public void handleAcceptReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {}

    @Override
    public void handleConnectReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {}

    @Override
    public void handleKeyCancelled_(SelectionKey k)
        throws FatalIOEventHandlerException
    {}

    //
    // members
    //

    /** the server who spawned us */
    private ZephyrServer _boss;

    /** This channel's id */
    private int _ourid;

    /** id of the channel we're sending packets to (othid = "other id") */
    private int _remid;

    /** single in-transit (i.e. could not be readimmediately) bytebuffer */
    private ByteBuffer _rdbuf;

    /** single in-transit (i.e. could not be written immediately) bytebuffer */
    private ByteBuffer _wrbuf;

     /** state the endpoint is in */
    private ServerConstants.EndpointState _state;

    private static Logger l = Util.l(PeerEndpoint.class);
}
