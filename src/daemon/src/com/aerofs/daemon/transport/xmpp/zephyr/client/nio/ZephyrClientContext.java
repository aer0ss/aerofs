/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio;

import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.PrioQueue;
import com.aerofs.daemon.transport.xmpp.XUtil;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IState;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IStateContext;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IStateEvent;
import com.aerofs.lib.C;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.id.DID;
import com.aerofs.zephyr.core.ExAlreadyBound;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import static com.aerofs.daemon.lib.DaemonParam.Zephyr.QUEUE_LENGTH;
import static com.aerofs.daemon.tng.xmpp.zephyr.Constants.ZEPHYR_BIND_MSG_LEN;
import static com.aerofs.daemon.tng.xmpp.zephyr.Constants.ZEPHYR_INVALID_CHAN_ID;
import static com.aerofs.daemon.tng.xmpp.zephyr.Constants.ZEPHYR_MSG_BYTE_ORDER;
import static com.aerofs.daemon.tng.xmpp.zephyr.Constants.ZEPHYR_REG_MSG_LEN;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ClientConstants.ZEPHYR_CLIENT_HDR_LEN;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEvent.BEGIN_CONNECT;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEvent.PENDING_OUT_PACKET;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEvent.RECVD_REMOTE_CHAN_ID;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientState.TERMINATED;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientUtil.handleError;
import static com.aerofs.zephyr.core.ZUtil.istdesc;

/**
 * Represents the state of a connection to a remote ZephyrClient. Let's say our
 * DID is A, and the remote is B. Then this class represents the state of our
 * connection from A to B via the intermediary Zephyr relay server. This class
 * is used by all the state functions in {@link ZephyrClientState}. I have tried
 * as much as possible to keep this class dumb.
 *
 * FIXME: remove as much of the logic as possible (i.e. make this dumb)
 */
public class ZephyrClientContext implements IStateContext
{
    /**
     * Constructor
     * @param local  local DID
     * @param remote DID of the remote peer to which we are sending data
     * @param k      {@link SelectionKey} via which we are notified of, or register
     * for i/o events
     * @param boss   {@link ZephyrClientManager} that owns us ([sigh] nice circular dependency)
     */
    ZephyrClientContext(DID local, DID remote, SelectionKey k, ZephyrClientManager boss)
    {
        assert local != null && remote != null :
            ("zc: attempt construct with invalid dids");

        assert k != null :
            ("zc: attempt construct with null key");

        assert boss != null :
            ("zc: attempt construct with no boss");

        // FIXME: as much as possible, move these out of the constructor when I have time

        _locdid = local;
        _remdid = remote;
        _k = k;
        _loczid = ZEPHYR_INVALID_CHAN_ID;
        _remzid = ZEPHYR_INVALID_CHAN_ID;
        _boss = boss;
        _ctrlhdrrcvd = false;
        _ctrlbodylen = 0;
        _ctrlbuf = ByteBuffer.allocate(ZEPHYR_CONTROL_MSG_BYTEBUFFER_SIZE);
        _ctrlbuf.order(ZEPHYR_MSG_BYTE_ORDER);
        _rdhdrrcvd = false;
        _rdbodylen = 0;
        _rdbodybytes = 0;
        _rdhdrbuf = ByteBuffer.allocate(XUtil.getHeaderLen());
        _rdhdrbuf.order(ZEPHYR_MSG_BYTE_ORDER);
        _rdbufs = null;
        _bytesrx = 0;
        _txq = new PrioQueue<Out>(QUEUE_LENGTH);
        _wrcurrout = null;
        _wrbodybytes = 0;
        _wrbufs = null;
        _bytestx = 0;
        _state = ZephyrClientState.NEW;
        _conntoserver = false;
        _haltex = null;
        _eq = new ArrayBlockingQueue<IStateEvent>(3);

        assert _ctrlbuf.capacity() >= Math.max(ZEPHYR_REG_MSG_LEN, ZEPHYR_BIND_MSG_LEN) :
               ("control buffer too small");
    }

    //
    // packet transmission
    //

    /**
     * Enqueues a packet for delivery to a remote peer
     * @param w {@link IResultWaiter} to be notified if transmission succeeded or failed
     * @param p priority of this packet
     * @param b array of byte arrays with all the data to be sent
     * @param o stream cookie (????)
     * @throws ExInvalidZephyrClient if the ZephyrClient was terminated
     * @throws ExNoResource if the outgoing-packet queue is full
     * @throws IOException if we cannot copy the bytes to our internal {@link Out} object
     */
    void enqueueSegment_(IResultWaiter w, Prio p, byte[][] b, Object o)
        throws ExInvalidZephyrClient, ExNoResource, IOException
    {
        if (_state == TERMINATED) throw new ExInvalidZephyrClient(toString() + ": TERMINATED", Cfg.did());
        if (_txq.isFull_()) throw new ExNoResource(toString() + ": txq full");

        {
            Out out = new Out(w, b, o);
            _txq.enqueue_(out, p);
            l.debug(toString() + ": +out b:" + out._length);
        }

        switch (_state) {
        case RECVING:
        case SENDING_AND_RECVING:
            if (_eq.peek() != PENDING_OUT_PACKET) _eq.add(PENDING_OUT_PACKET);
            _boss.reschedule_(this);
            break;
        default: break;
        }
    }

    //
    // IStateContext methods
    //

    @Override
    public IState<?> curr_()
    {
        return _state;
    }

    @Override
    public void next_(IState<?> next)
    {
        ZephyrClientState s = (ZephyrClientState) next;
        assert s != null : (toString() + ": attempt to set invalid state");

        if (_state != next) {
            l.info(toString() + ": T: " + _state + "->" + next);
        }

        _state = s;
    }

    @Override
    public IStateEvent dequeue_()
    {
        return _eq.poll();
    }

    @Override
    public void enqueueEvent_(IStateEvent ev)
    {
        boolean added = _eq.offer(ev);
        assert added : ("ev:" + ev + " not added");
    }

    @Override
    public Logger logger_()
    {
        return l;
    }

    //
    // ZephyrClientContext-specific methods
    //

    @Override
    public String toString()
    {
        return "zc[" + tinydesc() + "  " + istdesc(_k) + "]";
    }

    /**
     * @return a short description of this <code>ZephyrClientContext</code> of the form:
     * <code>{$local_did} + ({$local_zid}) -> {$remote_did} ({$remote_zid}</code>.
     * <br/>
     * <br/>
     * <strong>IMPORTANT:</strong> This method is ideal if you <em>do not</em>
     * want to print the key info (for example, you're in the shutdown process
     * and the key may be in an invalid state)
     */
    public String tinydesc()
    {
        return _locdid.toString() +" (" + _loczid + ") -> " + _remdid.toString() + " (" + _remzid + ")";
    }

    /**
     * @return a full description of this <code>ZephyrClientContext</code> of the form:
     * <code>zc[{$local_did} + ({$local_zid}) -> {$remote_did} ({$remote_zid} tx: b:{$bytestx}
     * rx: b:{$bytesrx} txq:{$txq.length} st:{$state} ists: {$selkey_interest_mask}]</code>
     * This description is ideal for populating the <code>diagnose</code> field
     * in {@link com.aerofs.proto.Files.PBDumpStat}
     * <br/>
     * <br/>
     * <strong>IMPORTANT:</strong> prints the key information, and asserts that
     * the key is still in a valid state!
     */
    public String hugedesc()
    {
        return "zc[" + tinydesc() + " tx: b:" + _bytestx + " rx: b:" + _bytesrx + " txq:" + _txq.length_() + " st:" + _state + " ists: " + istdesc(_k) + "]";
    }

    /**
     * Clean all buffers, indexes and counters associated with the control fields
     */
    void cleanCtrlFields_()
    {
        _ctrlhdrrcvd = false;
        _ctrlbodylen = 0;
        _ctrlbuf.clear();
    }

    /**
     * Clean all buffers, indexes and counters associated with reading code
     * (also returns {@link ByteBuffer} objects used by recv() back to the
     * {@link com.aerofs.zephyr.core.BufferPool})
     */
    void cleanRecvFields_()
    {
        _rdhdrrcvd = false;
        _rdhdrbuf.clear();
        _rdbodylen = 0;
        _rdbodybytes = 0;

        if (_rdbufs != null) {
            for (ByteBuffer b : _rdbufs) _boss.getBufferPool().putBuffer_(b);
            _rdbufs = null;
        }
    }

    /**
     * Clean all buffers, indexes and counters associated with writing code
     * (also returns {@link ByteBuffer} objects used by send() back to the
     * {@link com.aerofs.zephyr.core.BufferPool})
     *
     * @param returnToPool true if the buffers used for sending data should be
     * returned to the <code>BufferPool</code>, false otherwise
     */
    void cleanSendFields_(boolean returnToPool)
    {
        _wrcurrout = null;
        _wrbodybytes = 0;

        if (returnToPool) {
            for (ByteBuffer b: _wrbufs) _boss.getBufferPool().putBuffer_(b);
        }
        _wrbufs = null;
    }

    /**
     * Start this ZephyrClientContext up and get it ready to process incoming
     * and outgoing messages
     */
    void startup_()
    {
        l.info(toString() + ": start sm");

        if (_eq.isEmpty()) {
            _eq.add(BEGIN_CONNECT);
            _boss.reschedule_(this);
        } else {
            IStateEvent ev = _eq.peek();
            assert ev == BEGIN_CONNECT : (toString() + ": in NEW but ev:" + ev + " enqueued");
        }
    }

    /**
     * Perform the cleanup functions required to clean up this ZephyrClientContext
     * and marks the connection from local -> remote as TERMINATED
     *
     * @param e Exception to deliver to any {@link IResultWaiter} objects
     */
    void cleanup_(Exception e)
    {
        if (_haltex == null) {
            assert e != null : ("null e with null haltex");
            _haltex = e;
        }

        l.warn(toString() + ": shutting down: cause:" + _haltex);

        while (!_txq.isEmpty_()) {
            Out o = _txq.dequeue_();
            handleError(_remdid, o._waiter, _haltex, l);
        }

        cleanCtrlFields_();
        cleanRecvFields_();
        cleanSendFields_(false); // when in doubt...don't return these buffers to the pool

        _state = TERMINATED;
    }

    //
    // getters and setters
    //

    /**
     * @return <code>true</code> if this <code>ZephyrClientContext</code> was
     * terminated, <code>false</code> if it's still valid and will process events
     */
    boolean terminated_()
    {
        return _state == TERMINATED;
    }

    /**
     * Sets the remotezid to which we will be sending data. Also enqueues an
     * event onto the event queue and unparks the state machine for
     * this ZephyrClientContext object if it is currently waiting for
     * a zid from the remote.
     *
     * <strong>IMPORTANT:</strong> If this ZephyrClientContext object has just
     * been created you must call startup_ manually, otherwise Zephyr will
     * not establish connections. In other words, with a new ZephyrClientContext
     * DO NOT SET _remotezid AND JUST RETURN, OR YOU WILL WONDER WHY THE STATE
     * MACHINE SIMPLY ISN'T RUNNING!
     *
     * @param remotezid Zephyr ID of the remote peer's connection to Zephyr
     * @throws ExAlreadyBound if the remotezid has already been set to a valid zid
     *
     * FIXME: gotta figure out a clean way around this...
     */
    void setRemoteZid_(int remotezid)
        throws ExAlreadyBound
    {
        l.info(toString() + ": attempt set remote id:" + remotezid + " existing id:" + _remzid);

        assert _state != TERMINATED :
            (toString() + ": in TERMINATED state");

        assert remotezid != ZEPHYR_INVALID_CHAN_ID :
            (toString() + ": attempt to set remote id to invalid chan id");

        if (_remzid != ZEPHYR_INVALID_CHAN_ID) {
            throw new ExAlreadyBound(_remzid, remotezid);
        }

        _remzid = remotezid;

        l.info(toString() + ": succeed set remote id:" + _remzid);

        switch(_state) {
        case PREP_FOR_BINDING:
            _eq.add(RECVD_REMOTE_CHAN_ID);
            _boss.reschedule_(this);
            break;
        default: break;
        }
    }

    //
    // types
    //

    /**
     * Internal representation of an outgoing packet
     */
    class Out
    {
        Out(IResultWaiter waiter, byte[][] bss, Object strmCookie)
            throws IOException
        {
            _waiter = waiter;
            initInternalByteArrayFields_(bss);
            _cookie = strmCookie;
        }

        /**
         * Wraps the byte-arrays to be sent into a set of ByteBuffer objects
         * ready for gathering write by a {@link java.nio.channels.SelectableChannel}
         * <strong>IMPORTANT:</strong> changes to the underlying byte-arrays will
         * be reflected in the returned {@link ByteBuffer} objects
         * <strong>IMPORTANT: DO NOT RETURN THESE TO THE BUFFER POOL - THEY WILL THROW
         * THE NUM_BUFFERS_REQUIRED CALCULATIONS OFF!!!</strong>
         *
         * @return an array of <code>ByteBuffer</code> objects, one wrapping each
         * byte array to be sent out
         */
        ByteBuffer[] getWrappedByteArrays()
        {
            int idx = 0;
            ByteBuffer[] bbs = new ByteBuffer[_bss.length];
            for (byte[] bs : _bss) bbs[idx++] = ByteBuffer.wrap(bs);
            return bbs;
        }

        /**
         * Prepends the ZephyrClient message-header to the packet and sets up
         * the length fields
         * @param bss array of byte-arrays with data to be sent
         * @throws IOException if header generation failed
         */
        private void initInternalByteArrayFields_(byte[][] bss)
            throws IOException
        {

            // calculate how big the payload is
            int datalen = 0;
            for (byte[] bs : bss) datalen += bs.length;

            // write the header for this outgoing packet
            ByteArrayOutputStream baos =
                new ByteArrayOutputStream(ZEPHYR_CLIENT_HDR_LEN);
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(C.CORE_MAGIC);
            dos.writeInt(datalen);

            // serialize the header byte-array and check that it's ok
            byte[] hdr = baos.toByteArray();
            assert hdr.length == ZEPHYR_CLIENT_HDR_LEN :
                (ZephyrClientContext.this.toString() +
                         ": bad zephyr client header length exp:" + ZEPHYR_CLIENT_HDR_LEN + " act:" + hdr.length);

            // create a new bss array with space for the header byte-array
            _bss = new byte[bss.length + 1][];

            // copy the header
            _bss[0] = hdr;

            // copy the rest of the segments
            for (int inidx = 0, outidx = 1; inidx < bss.length; inidx++, outidx++) {
                _bss[outidx] = bss[inidx];
            }

            // now set length of the entire Out segment
            _length = datalen + _bss[0].length;

            // this is unnecessary, but a check because I once made an error in
            // the code above
            int outlen = 0;
            for (byte[] bs : _bss) outlen += bs.length;
            assert _length == outlen :
                (toString() + "bad out construct exp:" + outlen + " act:" + _length);
        }

        IResultWaiter _waiter;
        byte[][] _bss;
        int _length;
        Object _cookie;
    }

    //
    // members
    //

    /** local {@link DID} for this relay connection */
    final DID _locdid;

    /** remote {@link DID} for this relay connection */
    final DID _remdid;

    /** our {@link SelectionKey} via which we register or are notified of interest in i/o ops */
    final SelectionKey _k;

    /** our zephyr channel id */
    int _loczid;

    /** remote peer's zephyr channel id */
    int _remzid;

    /** the {@link ZephyrClientManager} that owns us */
    final ZephyrClientManager _boss;

    /** whether we've already received the control message header */
    boolean _ctrlhdrrcvd;

    /** length we expect for the incoming control message */
    int _ctrlbodylen;

    /** buffer to store in-progress Zephyr control messages (register/bind) */
    final ByteBuffer _ctrlbuf;

    /** whether we received the header of the current packet we're reading */
    boolean _rdhdrrcvd;

    /** length of the packet that we're reading */
    int _rdbodylen;

    /** how many bytes we've read of this packet already */
    int _rdbodybytes;

    /** buffer into which we always read the packet header */
    final ByteBuffer _rdhdrbuf;

    /** ByteBuffer objects into which we are receiving data */
    ByteBuffer[] _rdbufs;

    /** total number of bytes received from this DID */
    long _bytesrx;

    /** outgoing-message queue */
    final PrioQueue<Out> _txq;

    /** the current {@link Out} object being written out */
    Out _wrcurrout;

    /** how many bytes we've written of the current Out packet */
    int _wrbodybytes;

    /** ByteBuffer array holding the bytes that we're currently sending out */
    ByteBuffer[] _wrbufs;

    /** total number of bytes sent to this DID */
    long _bytestx;

    /** state this client connection is in */
    ZephyrClientState _state;

    /** whether the client has connected to the server at least */
    boolean _conntoserver;

    /** exception that caused the state machine to halt */
    Exception _haltex;

    /** logger for all instances of this class */
    static Logger l = com.aerofs.lib.Util.l(ZephyrClientContext.class);

    /** event queue into which the state machine functions feed events */
    private final Queue<IStateEvent> _eq;

    /** size of bytebuffer to hold zephyr control messages */
    private static final int ZEPHYR_CONTROL_MSG_BYTEBUFFER_SIZE = ZEPHYR_BIND_MSG_LEN * 3;
}
