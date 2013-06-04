/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc. 2011.
 */

package com.aerofs.zephyr.server;

import com.aerofs.base.Loggers;
import com.aerofs.zephyr.core.ExAlreadyBound;
import com.aerofs.zephyr.core.FatalIOEventHandlerException;
import com.aerofs.zephyr.core.IIOEventHandler;
import com.aerofs.zephyr.server.ServerConstants.EndpointState;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import static com.aerofs.lib.Util.crc32;
import static com.aerofs.zephyr.Constants.ZEPHYR_BIND_MSG_LEN;
import static com.aerofs.zephyr.Constants.ZEPHYR_BIND_PAYLOAD_LEN;
import static com.aerofs.zephyr.Constants.ZEPHYR_INVALID_CHAN_ID;
import static com.aerofs.zephyr.Constants.ZEPHYR_MAGIC;
import static com.aerofs.zephyr.Constants.ZEPHYR_MSG_BYTE_ORDER;
import static com.aerofs.zephyr.Constants.ZEPHYR_REG_MSG_LEN;
import static com.aerofs.zephyr.core.ZUtil.addInterest;
import static com.aerofs.zephyr.core.ZUtil.getSocketChannel;
import static com.aerofs.zephyr.server.ServerConstants.EndpointState.BOUND;
import static com.aerofs.zephyr.server.ServerConstants.EndpointState.CONNECTED;
import static com.aerofs.zephyr.server.ServerConstants.EndpointState.REGISTERED;
import static com.aerofs.zephyr.server.ServerConstants.ReadStatus.EOF;
import static com.aerofs.zephyr.server.ServerConstants.ReadStatus.HAS_BYTES;
import static com.aerofs.zephyr.server.ServerConstants.ReadStatus.NO_BYTES;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.channels.SelectionKey.OP_WRITE;

/**
 * - find a way to avoid looking up the id in the map every single time
 * - need setstate() function
 */
public class PeerEndpoint implements IIOEventHandler
{

    public PeerEndpoint(int id, String ouraddr, ZephyrServer boss)
    {
        checkArgument(id != ZEPHYR_INVALID_CHAN_ID, "invalid id:" + id);

        // FIXME (AG): don't allocate/deallocate the read/write buffer
        // (i.e. keep only one buffer in play between the peers)

        _boss = boss;
        _ourid = id;
        _ouraddr = ouraddr;
        _remid = ZEPHYR_INVALID_CHAN_ID;
        _remaddr = null;
        _ctrlb = null;
        _wrbuf = null;
        _wrcrc = null;
        _txbuf = boss.getBuffer(); // a single buffer (we don't allocate/deallocate this one)
        _txcount = 0;
        _state = CONNECTED;

        l.info("{}: created", compact());
    }

    public void init(SelectionKey k)
        throws IOException
    {
        checkState(_state == CONNECTED, compact() + ": invalid init state:" + _state);
        checkState(_wrbuf == null && _wrcrc == null, compact() + ": init w/ pending wrbuf:" + _wrbuf);

        try {
            ByteBuffer reg = _boss.getBuffer();
            checkArgument(reg.capacity() >= ZEPHYR_REG_MSG_LEN, compact() + ": insufficient cap:" + reg.capacity());

            reg = createRegistrationMessage(reg, _ourid);
            reg.flip();

            if (writefully(k, reg)) {
                transition(REGISTERED);
            }
        } catch (IOException e) {
            handleException(e, "fail reg write");
            throw e;
        }
    }

    @Override
    public void handleWriteReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {
        if (!k.isValid()) return;

        checkArgument(k.isWritable(), compact() + ": unwritable k:" + k);
        checkNotNull(_wrbuf, compact() + " wrbuf:" + _wrbuf + " wrcrc:" + _wrcrc);
        checkNotNull(_wrcrc, compact() + " wrbuf:" + _wrbuf + " wrcrc:" + _wrcrc);

        try {
            boolean allwritten = writefully(k);
            if (!allwritten) {
                return;
            }

            if (_remid != ZEPHYR_INVALID_CHAN_ID) {
                checkState(_state != CONNECTED, "bound remid:" + _remid + " state:" + _state);
                PeerEndpoint srcpe = _boss.getPeerEndpoint(_remid);
                srcpe.writeFinished();
            }

            if (_state == CONNECTED) {
                // we will not be writing out anything _but_ the registration
                // message if we're in the connected state (i.e. we don't have
                // to worry that another endpoint was writing to us). therefore
                // it is safe for us to transition to the registered state
                transition(REGISTERED);
            }
        } catch (ExInvalidPeerEndpoint e) {
            handleException(e, "invalid dstpe:" + _remid);
        } catch (IOException e) {
            handleException(e, "fail wr");
        } catch (Exception e) {
            handleException(e, "unexpected");
        }
    }

    @Override
    public void handleReadReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {
        if (!k.isValid()) return;

        checkState(k.isReadable(), compact() + ": k:" + k + ":not readable");

        try {
            switch (_state) {
                case REGISTERED:
                    processInRegisteredState(k);
                    break;
                case BOUND:
                    processInBoundState(k);
                    break;
                default:
                    throw new IllegalStateException(compact() + ": rd unexpected in state:" + _state);
            }
        } catch (Exception e) {
            handleException(e, "fail rd");
        }
    }

    private void processInRegisteredState(SelectionKey k)
    {
        checkState(_state == REGISTERED, compact() + ": state:" + _state + ":invalid for func");

        SocketChannel ch = getSocketChannel(k);

        // FIXME: I should only read in len, and then bytes to be fwd compatible
        ByteBuffer b = (_ctrlb == null ? _boss.getBuffer() : _ctrlb);
        b.limit(ZEPHYR_BIND_MSG_LEN); // idempotent
        try {
            if (read(k, ch, b) != HAS_BYTES) {
                return;
            }

            // check for incomplete reads

            if (b.hasRemaining()) {
                checkState(_ctrlb == null || (b == _ctrlb), compact() + ": mismatched rd buf");

                l.trace("{}: bind msg incomplete exp:{} act:{}", compact(), ZEPHYR_BIND_MSG_LEN, b.position());

                addInterest(k, SelectionKey.OP_READ);
                _ctrlb = b;
                b = null; // use to prevent b from being put back into the pool (finally block)
                return;
            }

            _ctrlb = null;

            // check the message format and parameters

            b.flip();

            l.trace("{}: bind buf:{}", compact(), crc(b));

            byte[] magic = new byte[ZEPHYR_MAGIC.length];
            b.get(magic);
            if (!Arrays.equals(magic, ZEPHYR_MAGIC)) {
                terminate("bad zephyr msg exp:" + Arrays.toString(ZEPHYR_MAGIC) + " act:" + Arrays.toString(magic));
                return;
            }

            int len = b.getInt(); // ignore length value; simply use to move the position
            checkArgument(len == ZEPHYR_BIND_PAYLOAD_LEN,
                    "bad bind payload len exp:" + ZEPHYR_BIND_PAYLOAD_LEN + " act:" + len);

            int dstid = b.getInt(); // get the dst id we're trying to bind to
            PeerEndpoint dstpe = null;
            try {
                // bind (remote)

                dstpe = _boss.getPeerEndpoint(dstid);
                dstpe.bind(_ourid, _ouraddr);

                // bind (our side)

                bind(dstid, dstpe.getSourceAddress());
                transition(BOUND);

                // signal readiness to read again

                addInterest(k, SelectionKey.OP_READ);
            } catch (ExInvalidPeerEndpoint e) {
                handleException(e, "invalid pe:" + dstid);
                return;
            } catch (ExAlreadyBound e) {
                int prevBound = dstpe._remid;
                handleException(e, "attempted rebind prev:" + prevBound + " new:" + dstid);
                return;
            }
        } catch (IOException e) {
            _ctrlb = null; // keep this here...ensures that putBuffer_ only ever called once
            handleException(e, "fail rd");
        } finally {
            if (b != null) _boss.putBuffer(b);
        }
    }

    private void processInBoundState(SelectionKey k)
    {
        checkState(_state == BOUND, compact() + ":state:" + _state + ":invalid for func");

        SocketChannel ch = getSocketChannel(k);

        // ----

        // needs to be synchronized wrt. dest because in the interval between
        // hasPendingWrite and write someone else may write
        // is this even possible? I don't think so, since there should only ever
        // be a one-to-one mapping between ourid and _remid

        // this is just asking for trouble...splitting up hasPendingWrite and write
        // and doing this passedOwnership thing

        PeerEndpoint dstpe;
        try {
            dstpe = _boss.getPeerEndpoint(_remid);
        } catch (ExInvalidPeerEndpoint e) {
            handleException(e, "invalid dstpe:" + _remid);
            return;
        }

        if (dstpe.hasPendingWrite()) {
            // do not read! this will get the sender's flow control working...
            // assumes that we are level triggered
            l.trace("{}: wr pending to dst:{} do not read", compact(), _remid);
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
            checkNotNull(dkey, compact() + ": null key for dst:" + _remid);

            // we've received a few bytes to be transmitted to the dest peer

            b.flip();

            passedOwnership = true;
            boolean allwritten = dstpe.relay(dkey, b);
            if (allwritten) {
                addInterest(k, SelectionKey.OP_READ);
            }
        } catch (IOException e) {
            if (!passedOwnership) _boss.putBuffer(b);
            handleException(e, "fail relay");
        }

        // ----
    }

    /**
     * @return whether an {@link ByteBuffer} is already buffered for write
     */
    public boolean hasPendingWrite()
    {
        boolean haswrbuf = _wrbuf != null;
        boolean haswrcrc = _wrcrc != null;

        checkState(haswrbuf == haswrcrc, compact() + ": mismatch wrbuf:" + _wrbuf + " wrcrc:" + _wrcrc);

        return haswrbuf;
    }

    /**
     *
     * @param id channel id to which to bind this PeerEndpoint object to
     * @param addr remote address of the endpoint that initiated the bind
     * @throws ExAlreadyBound if the channel is already bound to any other zid
     */
    public void bind(int id, String addr)
        throws ExAlreadyBound
    {
        checkArgument(id != ZEPHYR_INVALID_CHAN_ID, compact() + ": invalid id:" + id + " for bind");

        if (_remid != ZEPHYR_INVALID_CHAN_ID && _remid != id) {
            throw new ExAlreadyBound(_remid, id);
        }

        _remid = id;
        _remaddr = addr;
    }

    /**
     * Cleans up resources allocated by this PeerEndpoint object and also
     * calls terminate() on the destination PeerEndpoint object. Also removes
     * this PeerEndpoint from the Zephyr server.
     * @param cause
     */
    public void terminate(String cause)
    {
        //
        // clean ourself up
        //

        if (_ctrlb != null) {
            _boss.putBuffer(_ctrlb);
            _ctrlb = null;
        }

        if (_wrbuf != null) {
            _boss.putBuffer(_wrbuf);
            _wrbuf = null;
            _wrcrc = null;
        }

        if (_txbuf != null) {
            _boss.putBuffer(_txbuf);
            _txbuf = null;
        }

        _boss.removeEndpoint(_ourid);

        //
        // now cleanup our destination
        //

        try {
            PeerEndpoint dstpe = _boss.getPeerEndpoint(_remid);
            dstpe.terminate("rem terminated cause:" + cause);
        } catch (ExInvalidPeerEndpoint e) {
            // ignore
        }

        l.warn("{}: terminate cause:{} tx:{}", compact(), cause, _txcount);
    }

    //
    // local utility
    //

    private void transition(EndpointState newstate)
    {
        EndpointState oldstate = _state;
        _state = newstate;

        l.info("{}: [{}->{}]", compact(), oldstate, newstate);
    }

    private void handleException(Exception e, String errmsg)
    {
        terminate(errmsg + " (" + e + ")");
    }

    /**
     * Called by the destination PeerEndpoint object when it has finished writing
     * a block that we buffered at some time in the past
     */
    private void writeFinished()
    {
        SelectionKey k = _boss.getSelectionKey(_ourid);
        k = checkNotNull(k, compact() + ": null self key");

        if (!k.isValid()) return;

        addInterest(k, SelectionKey.OP_READ);
    }

    // FIXME (AG): too many write-related calls

    /**
     * Writes a buffer if possible to this PeerEndpoint object's channel. Stores
     * the passed-in {@link ByteBuffer} as a pending buffer if it could not write
     * all bytes in one shot. Also verifies that there are no pending writes
     * already.
     *
     * <strong>by calling this function you <strong>pass ownership</strong>
     * of the buffer {@code b}</strong>
     *
     * @param k {@link SelectionKey} this PeerEndpoint object's key
     * (for re-registering interest)
     * @param b ByteBuffer into which to read bytes
     * @return whether all the bytes were written or not
     *
     * @throws IOException on any write exception
     */
    private boolean relay(SelectionKey k, ByteBuffer b)
        throws IOException
    {
        try {
            return writefully(k, b);
        } catch (IOException e) {
            handleException(e, "fail wr");
            throw e;
        }
    }

    private boolean writefully(SelectionKey k, ByteBuffer b)
        throws IOException
    {
        checkState(_wrbuf == null && _wrcrc == null, compact() + ": pending wr wrbuf:" + _wrbuf + " wrcrc:" + _wrcrc);

        setwrparams(b);

        return writefully(k);
    }

    private void setwrparams(@Nullable ByteBuffer b)
    {
        _txbuf.clear();

        if (b != null) {
            _wrbuf = b;
            _wrcrc = crc(b);

            l.trace("{} wrbuf pos:{} rem:{} crc:{}", compact(), _wrbuf.position(), _wrbuf.remaining(), _wrcrc);
        } else {
            _wrbuf = null;
            _wrcrc = null;
        }
    }

    private boolean writefully(SelectionKey k)
            throws IOException
    {
        ByteBuffer intransit = _wrbuf;
        try {
            boolean allwritten = write(k, intransit);
            if (allwritten) { // FIXME (AG): think of a better way to do this

                // setup the crc-check buffer properly

                _txbuf.limit(_txbuf.position());
                _txbuf.position(0);

                // check whether the buffer is ok

                checkState(crc(_txbuf).equals(_wrcrc), "bad wr crc exp:" + _wrcrc + " act:" + crc(_txbuf));

                // reset buffer

                setwrparams(null);
            }
            return allwritten;
        } finally {
            if (_wrbuf == null) {
                _boss.putBuffer(intransit);
            }
        }
    }

    private void copytxbytes(ByteBuffer b, int begpos, int finpos, int bytetx, int bytrem)
            throws IOException
    {
        int txbufpos = _txbuf.position();
        int txbufrem = _txbuf.remaining();

        try {
            b.position(begpos);

            byte[] tx = new byte[bytetx];
            b.get(tx);
            _txbuf.put(tx);

            checkState(b.position() == finpos, "bad pos exp:" + finpos + " act:" + b.position());
        } catch (BufferOverflowException e) {
            String errmsg = "wrbuf:[beg:" + begpos + " tx:" + bytetx + " fin:" + finpos + " rem:" + bytrem + "] " +
                            "txbuf:[beg:" + txbufpos + " rem:" + txbufrem + "]";

            throw new IOException(errmsg, e);
        }
    }

    /**
     * Write out all the remaining bytes in a given {@code ByteBuffer}
     *
     * @param k {@code SelectionKey} for the fd over which the data is written
     * @param b {@code ByteBuffer} with the bytes to be written
     * @return <strong>true</strong> if {@code b.hasRemaining() == false} after the write
     * @throws IOException
     */
    private boolean write(SelectionKey k, ByteBuffer b)
        throws IOException
    {
        SocketChannel ch = getSocketChannel(k);
        try {
            int begpos = b.position();

            int bytetx = ch.write(b); // actual write call!
            _txcount += bytetx;

            int finpos = b.position();
            int bytrem = b.remaining();

            copytxbytes(b, begpos, finpos, bytetx, bytrem);

            if (b.hasRemaining()) {
                addInterest(k, OP_WRITE);
                return false;
            }

            return true;
        } catch (IOException e) {
            l.warn("{}: ch:{} fail wr err:{}", compact(), ch, e);
            throw e;
        }
    }

    /**
     * Performs a read() on a {@link SocketChannel}. Calls terminate() if the
     * channel returns EOF, and reregisters for interest if no bytes were available
     * on this read. Otherwise, the passed-in {@link ByteBuffer} is populated with
     * the read bytes
     *
     * <strong>caller is responsible for allocating and releasing buffer {@code b}</strong>
     * {@code b} should be released if ReadStatus.EOF or
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
        ServerConstants.ReadStatus status;

        int rd = ch.read(b);
        switch (rd) {
            case -1:
                status = EOF;
                terminate("rd EOF");
                break;
            case 0:
                status = NO_BYTES;
                addInterest(k, SelectionKey.OP_READ);
                break;
            default:
                checkState(rd > 0, compact() + ": bad bytes rd:" + rd);
                status = HAS_BYTES;
        }

        return status;
    }

    /**
     * @return the remote address from which our peer is sending data
     */
    String getSourceAddress()
    {
        return _ouraddr;
    }

    /**
     * @return compact representation of this PeerEndpoint
     */
    private String compact()
    {
        String us = _ourid + " (" + _ouraddr + ")";
        String other = (_remid == ZEPHYR_INVALID_CHAN_ID ? "0000 (NONE)" : _remid + " (" + _remaddr + ")");

        return us + " ==> " + other;
    }

    /**
     * @return full string representation of our object
     */
    public String toString()
    {
        return "pe:" + _ourid + " (" + _ouraddr + ") dst:" + _remid + " (" + _remaddr + ") state:" + _state + " wr:[buf:" + _wrbuf + " crc:" + _wrcrc + "]";
    }

    //
    // utility
    //

    /**
     * Populates a {@link java.nio.ByteBuffer} with a Registration message
     * <strong>does not clear() or flip() the ByteBuffer prior to, or after use</strong>
     *
     * @param b ByteBuffer to populate
     * @param id connection id
     * @return the same ByteBuffer passed in
     */
    static ByteBuffer createRegistrationMessage(ByteBuffer b, int id)
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

    static String crc(ByteBuffer buf)
    {
        int startpos = buf.position();
        try {
            byte[] contents = new byte[buf.remaining()];
            return crc32(contents);
        } finally {
            buf.position(startpos);
        }
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

    /** this peer's channel id */
    private int _ourid;

    /** id of the channel we're sending packets to (remid = "rem id") */
    private int _remid;

    /** source address for this peer */
    private final String _ouraddr;

    /** remote address of the peer to which we're bound */
    private String _remaddr;

    /** single {@code ByteBuffer} that holds the bind-message being read from the client */
    private ByteBuffer _ctrlb;

    /** single in-transit chunk of data to be relayed to the destination peer */
    private ByteBuffer _wrbuf;

    /** crc of the data to be relayed to the destination peer */
    private String _wrcrc;

    /** bytes written out over the wire */
    private long _txcount;

    /**
     * buffer that holds the data written by a single
     * {@link PeerEndpoint#writefully(java.nio.channels.SelectionKey)} call
     * at any point and time {@code _txbuf} will hold only <em>part</em> of the bytes in _wrbuf
     * <strong>FOR DEBUGGING ONLY!</strong>
     */
    private ByteBuffer _txbuf;

    /** state the endpoint is in */
    private ServerConstants.EndpointState _state;

    /** our logger */
    private static Logger l = Loggers.getLogger(PeerEndpoint.class);
}
