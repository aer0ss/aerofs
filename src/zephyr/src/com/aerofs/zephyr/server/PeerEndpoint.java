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

import static com.aerofs.zephyr.Constants.ZEPHYR_BIND_MSG_LEN;
import static com.aerofs.zephyr.Constants.ZEPHYR_BIND_PAYLOAD_LEN;
import static com.aerofs.zephyr.Constants.ZEPHYR_INVALID_CHAN_ID;
import static com.aerofs.zephyr.Constants.ZEPHYR_MAGIC;
import static com.aerofs.zephyr.Constants.ZEPHYR_REG_MSG_LEN;
import static com.aerofs.zephyr.core.ZUtil.addInterest;
import static com.aerofs.zephyr.core.ZUtil.getSocketChannel;
import static com.aerofs.zephyr.server.ServerConstants.EndpointState.BOUND;
import static com.aerofs.zephyr.server.ServerConstants.EndpointState.CONNECTED;
import static com.aerofs.zephyr.server.ServerConstants.EndpointState.REGISTERED;
import static com.aerofs.zephyr.server.ServerConstants.ReadStatus.EOF;
import static com.aerofs.zephyr.server.ServerConstants.ReadStatus.HAS_BYTES;
import static com.aerofs.zephyr.server.ServerConstants.ReadStatus.NO_BYTES;
import static com.aerofs.zephyr.server.ZephyrServerUtil.crc;
import static com.google.common.base.Preconditions.checkState;

/**
 * - find a way to avoid looking up the id in the map every single time
 * - need setstate() function
 */
public class PeerEndpoint implements IIOEventHandler
{
    public PeerEndpoint(int id, ZephyrServer boss)
    {
        assert id != ZEPHYR_INVALID_CHAN_ID : ("invalid id:" + id);

        // FIXME (AG): don't allocate/deallocate the read/write buffer
        // (i.e. keep only one buffer in play between the peers)

        _boss = boss;
        _ourid = id;
        _remid = ZEPHYR_INVALID_CHAN_ID;
        _rdsqn = -1;
        _wrsqn = -1;
        _ctrlb = null;
        _wrbuf = null;
        _wrcrc = null;
        _txbuf = boss.getBuffer(); // a single buffer (we don't allocate/deallocate this one)
        _state = CONNECTED;

        l.info(compact() + ": created");
    }

    public void init(SelectionKey k)
        throws IOException
    {
        assert _state == CONNECTED : (compact() + ": invalid init state:" + _state);
        assert _wrbuf == null && _wrcrc == null: (compact() + ": init w/ pending wrbuf:" + _wrbuf);

        try {
            ByteBuffer reg = _boss.getBuffer();
            assert reg.capacity() >= ZEPHYR_REG_MSG_LEN;
            reg = ZephyrServerUtil.createRegistrationMessage(reg, _ourid);
            reg.flip();

            boolean allwritten = writefully(k, reg);
            if (allwritten) {
                transition(REGISTERED, "fin reg");
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

        assert k.isWritable() : (compact() + ": unwritable k:" + k);
        assert _wrbuf != null && _wrcrc != null :
            (compact() + " wrbuf:" + _wrbuf + " wrcrc:" + _wrcrc);

        try {
            boolean allwritten = writefully(k);
            if (!allwritten) {
                return;
            }

            if (_remid != ZEPHYR_INVALID_CHAN_ID) {
                assert _state != CONNECTED : ("bound remid:" + _remid + " state:" + _state);
                PeerEndpoint srcpe = _boss.getPeerEndpoint(_remid);
                srcpe.writeFinished();
            }

            if (_state == CONNECTED) {
                // we will not be writing out anything _but_ the registration
                // message if we're in the connected state (i.e. we don't have
                // to worry that another endpoint was writing to us). therefore
                // it is safe for us to transition to the registered state
                transition(REGISTERED, "fin reg");
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
                    assert false : (compact() + ": rd unexpected in state:" + _state);
            }
        } catch (Exception e) {
            handleException(e, "fail rd");
        }
    }

    private void processInRegisteredState(SelectionKey k)
    {
        assert _state == REGISTERED : (compact() + ": state:" + _state + ":invalid for func");

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
                assert _ctrlb == null || (b == _ctrlb) : (compact() + ": mismatched rd buf");

                l.debug(compact() +
                     ": bind msg incomplete exp:" + ZEPHYR_BIND_MSG_LEN + " act:" + b.position());

                addInterest(k, SelectionKey.OP_READ);
                _ctrlb = b;
                b = null; // use to prevent b from being put back into the pool (finally block)
                return;
            }

            _ctrlb = null;

            // check the message format and parameters

            b.flip();

            if (l.isDebugEnabled()) {
                l.debug(compact() + ": bind buf:" + crc(b));
            }

            byte[] magic = new byte[ZEPHYR_MAGIC.length];
            b.get(magic);
            if (!Arrays.equals(magic, ZEPHYR_MAGIC)) {
                l.warn(compact() + ": bad zephyr msg exp:" + ZEPHYR_MAGIC + " act:" + magic);
                terminate();
                return;
            }

            int len = b.getInt(); // ignore length value; simply use to move the position
            assert len == ZEPHYR_BIND_PAYLOAD_LEN :
                    ("bad bind payload len exp:" + ZEPHYR_BIND_PAYLOAD_LEN + " act:" + len);

            int dstid = b.getInt(); // get the dst id we're trying to bind to
            PeerEndpoint dstpe = null;
            try {
                dstpe = _boss.getPeerEndpoint(dstid);
                boolean remfirstbind = dstpe.bind(_ourid);

                // bind (from our side)

                _remid = dstid;
                transition(BOUND, "[" + _ourid + (remfirstbind ? "==>" : "<=>") + _remid + "]");

                // signal readiness to read again

                addInterest(k, SelectionKey.OP_READ);
            } catch (ExInvalidPeerEndpoint e) {
                handleException(e, "invalid pe:" + dstid);
                return;
            } catch (ExAlreadyBound e) {
                assert dstpe != null;
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
        assert _state == BOUND : (compact() + ":state:" + _state + ":invalid for func");

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

        if (dstpe.hasPendingWrite())
        {
            // do not read! this will get the sender's flow control working...
            // assumes that we are level triggered
            l.debug(compact() + ": wr pending to dst:" + _remid + " do not read");
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

        assert haswrbuf == haswrcrc :
            (compact() + ": mismatch wrbuf:" + _wrbuf + " wrcrc:" + _wrcrc);

        return haswrbuf;
    }

    /**
     * @param id channel id to which to bind this PeerEndpoint object to
     * @return true if this was the first time this {@code PeerEndpoint} was bound
     * @throws ExAlreadyBound if the channel is already bound to any other zid
     */
    public boolean bind(int id)
        throws ExAlreadyBound
    {
        assert id != ZEPHYR_INVALID_CHAN_ID : (compact() + ": invalid id:" + id + " for bind");

        // should this be an assert instead?
        if (_remid != ZEPHYR_INVALID_CHAN_ID && _remid != id) {
            throw new ExAlreadyBound(_remid, id);
        }

        boolean firstbind = (_remid == ZEPHYR_INVALID_CHAN_ID);
        _remid = id;
        return firstbind;
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

        _rdsqn = 0;
        _wrsqn = 0;

        _boss.removeEndpoint(_ourid);

        //
        // now cleanup our destination
        //

        try {
            PeerEndpoint dstpe = _boss.getPeerEndpoint(_remid);
            dstpe.terminate();
        } catch (ExInvalidPeerEndpoint e) {
            // ignore
        }
    }

    //
    // local utility
    //

    private void transition(EndpointState newstate, String transmsg)
    {
        EndpointState oldstate = _state;
        _state = newstate;

        l.info(compact() + ":" + transmsg + " [" + oldstate + "->" + newstate + "]");
    }

    private void handleException(Exception e, String errmsg)
    {
        l.warn(compact() + ": " + errmsg + " err:" + e);
        e.printStackTrace();
        terminate();
    }

    /**
     * Called by the destination PeerEndpoint object when it has finished writing
     * a block that we buffered at some time in the past
     */
    private void writeFinished()
    {
        SelectionKey k = _boss.getSelectionKey(_ourid);
        assert k != null : (compact() + ": null self key");

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
        _rdsqn++;

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
        assert _wrbuf == null && _wrcrc == null :
            (compact() + ": pending wr wrbuf:" + _wrbuf + " wrcrc:" + _wrcrc);

        setwrparams(b);

        if (l.isTraceEnabled()) {
            l.trace(compact() + " beg wr wrbuf:" + _wrbuf + " wrcrc:" + _wrcrc);
        }

        return writefully(k);
    }

    private void setwrparams(@Nullable ByteBuffer b)
    {
        if (b != null) {
            _wrbuf = b;
            _wrcrc = crc(b);
        } else {
            _wrbuf = null;
            _wrcrc = null;
        }

        _txbuf.clear();
    }

    private boolean writefully(SelectionKey k)
            throws IOException
    {
        ByteBuffer intransit = _wrbuf;
        try {
            boolean allwritten = write(k, intransit);
            if (allwritten) { // FIXME (AG): think of a better way to do this
                _wrsqn++;

                assert _rdsqn == _wrsqn : ("bad wr seqnum exp:" + _wrsqn + " act:" + _rdsqn);
                assert crc(_txbuf) == _wrcrc : ("bad wr crc exp:" + _wrcrc + " act:" + crc(_txbuf));

                setwrparams(null);
            }
            return allwritten;
        } finally {
            if (_wrbuf == null) {
                _boss.putBuffer(intransit);
            }
        }
    }

    private void copytxbytes(ByteBuffer b, int bytetx, int begpos, int finpos)
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
            String errmsg = "wrbuf:[beg:" + begpos + " tx:" + bytetx + " fin:" + finpos + " siz:" + b.capacity() + "] " +
                            "txbuf:[beg:" + txbufpos + " rem:" + txbufrem + "siz:" + _txbuf.capacity() + "]";
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
            int bytetx = ch.write(b);
            int finpos = b.position();

            copytxbytes(b, bytetx, begpos, finpos);

            if (b.hasRemaining()) {
                addInterest(k, SelectionKey.OP_WRITE);
                return false;
            }

            return true;
        } catch (IOException e) {
            l.warn(compact() + ": ch:" + ch + " err on wr:" + e);
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
     * @return compact representation of this PeerEndpoint
     */
    private String compact()
    {
        return "pe:" + _ourid;
    }

    /**
     * @return full string representation of our object
     */
    public String toString()
    {
        return "pe:" + _ourid + " dst:" + _remid + " state:" + _state + " wr:[buf:" + _wrbuf + " crc:" + _wrcrc + "]";
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

    /** single {@code ByteBuffer} that holds the bind-message being read from the client */
    private ByteBuffer _ctrlb;

    /** single in-transit chunk of data to be relayed to the destination peer */
    private ByteBuffer _wrbuf;

    /** crc of the data to be relayed to the destination peer */
    private String _wrcrc;

    /**
     * buffer that holds the data written by a single
     * {@link PeerEndpoint#writefully(java.nio.channels.SelectionKey)} call
     * at any point and time {@code _txbuf} will hold only <em>part</em> of the bytes in _wrbuf
     * <strong>FOR DEBUGGING ONLY!</strong>
     */
    private ByteBuffer _txbuf;

    /**
     * represents the nth <em>buffer</em> (each buffer contains n bytes)
     * <em>to be</em> relayed
     */
    private long _rdsqn;

    /**
     * represents the nth <em>buffer</em> (each buffer contains n bytes)
     * <em>successfully</em> relayed
     */
    private long _wrsqn;

     /** state the endpoint is in */
    private ServerConstants.EndpointState _state;

    private static Logger l = Loggers.getLogger(PeerEndpoint.class);
}
