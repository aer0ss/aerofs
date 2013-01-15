/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio;

import com.aerofs.base.BaseParam.Zephyr;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.CoreEvent;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IState;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IStateEventType;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.StateMachineEvent;
import com.aerofs.lib.Param;
import com.aerofs.proto.Transport.PBZephyrCandidateInfo;
import com.aerofs.zephyr.core.ExAlreadyBound;
import com.aerofs.zephyr.core.ZUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_MAGIC;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_REG_MSG_LEN;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_SERVER_HDR_LEN;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.BOUND;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.CONNECTED;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.HANDSHAKE_COMPLETE;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.PENDING_OUT_PACKET;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.PREPARED_FOR_BINDING;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.PREPARED_FOR_CONNECT;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.PREPARED_FOR_REGISTRATION;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.RECVD_ACK;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.RECVD_SYN;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.RECVD_SYNACK;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.REGISTERED;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.SENT_SYN;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.SENT_SYNACK;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.WRITE_COMPLETE;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.CoreEvent.HALT;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.CoreEvent.PARK;

/**
 * State functions for a ZephyrClient
 *
 * operates on {@link ZephyrClientContext} context objects only.
 * <strong>Make sure this enum _never_ holds any state. EVER.</strong>
 */
public enum ZephyrClientState implements IState<ZephyrClientContext>
{

    // NOTE: the default name of an enum is its face name
    // i.e. PREP_FOR_CONNECT, etc. This is a sane default, and perfectly acceptable
    // for my uses

    //
    // state machine functions
    //

    PREP_FOR_CONNECT
    {
        @Override
        public StateMachineEvent process_(StateMachineEvent ev, ZephyrClientContext ctx)
        {
            assertValidStateFunc(ctx, this);
            assertValidZephyrClientContext(ctx);

            SocketChannel sc = ZUtil.getSocketChannel(ctx._k);
            try {
                sc.connect(Zephyr.zephyrAddress());
            } catch (IOException e) {
                return halt_(ctx, "connect failed", e);
            }

            return new StateMachineEvent(PREPARED_FOR_CONNECT);
        }
    },
    CONNECTING
    {
        @Override
        public StateMachineEvent process_(StateMachineEvent ev, ZephyrClientContext ctx)
        {
            assertValidStateFunc(ctx, this);
            assertValidZephyrClientContext(ctx);

            SocketChannel sc = ZUtil.getSocketChannel(ctx._k);
            try {
                if (!sc.finishConnect()) {
                    ZephyrClientContext.l.debug(ctx + ": pending connect");
                    return park_(ctx, SelectionKey.OP_CONNECT);
                }
            } catch (IOException e) {
                return halt_(ctx, "connect failed", e);
            }

            ctx._conntoserver = true;

            return new StateMachineEvent(CONNECTED);
        }
    },
    PREP_FOR_REGISTRATION
    {
        @Override
        public StateMachineEvent process_(StateMachineEvent ev, ZephyrClientContext ctx)
        {
            assertValidStateFunc(ctx, this);
            assertValidZephyrClientContext(ctx);

            ctx.cleanCtrlFields_();

            return new StateMachineEvent(PREPARED_FOR_REGISTRATION);
        }
    },
    REGISTERING
    {
        @Override
        public StateMachineEvent process_(StateMachineEvent ev, ZephyrClientContext ctx)
        {
            assertValidStateFunc(ctx, this);
            assertValidZephyrClientContext(ctx);

            if (!ctx._k.isReadable()) {
                return park_(ctx, SelectionKey.OP_READ);
            }

            try {
                if (!readzid(ctx)) {
                    return park_(ctx, SelectionKey.OP_READ);
                }

                ctx.cleanCtrlFields_(); // don't really have to do this

                return new StateMachineEvent(REGISTERED);
            } catch (ExAbortState e) {
                ctx.cleanCtrlFields_();
                return halt_(ctx, e.getAbortMsg(), e.getAbortException());
            }
        }
    },
    HANDSHAKE
    {
        @Override
        public StateMachineEvent process_(StateMachineEvent ev, ZephyrClientContext ctx)
        {
            assertValidStateFunc(ctx, this);
            assertValidZephyrClientContext(ctx);

            IStateEventType type = ev.type();

            assert type == RECVD_SYN || type == REGISTERED : (ctx + ": unexpected ev:" + type);
            assert ctx._remzid == ZEPHYR_INVALID_CHAN_ID : (ctx + ": existing remote zid:" + ctx._remzid);

            if (type == REGISTERED) {
                return synRemote(ctx);
            }

            PBZephyrCandidateInfo zi = getSignallingData(ev);
            assert zi.getSourceZephyrId() != ZEPHYR_INVALID_CHAN_ID : (ctx + ": bad SYN:" + zi);

            try {
                StateMachineEvent retev;

                if (shouldDefer(ctx, zi)) {
                    ctx.setRemoteZid_(zi.getSourceZephyrId());
                    retev = synackRemote(ctx);
                } else {
                    retev = synRemote(ctx);
                }

                return retev;
            } catch (ExAlreadyBound e) {
                return halt_(ctx, "handshake failed", e);
            }
        }
    },
    WAIT_FOR_HANDSHAKE_SYNACK
    {
        @Override
        public StateMachineEvent process_(StateMachineEvent ev, ZephyrClientContext ctx)
        {
            assertValidStateFunc(ctx, this);
            assertValidZephyrClientContext(ctx);

            IStateEventType type = ev.type();
            if (type == SENT_SYN) return PARK; // wait for SYNACK

            PBZephyrCandidateInfo zi = getSignallingData(ev);

            if (type == RECVD_SYN && shouldDefer(ctx, zi)) {
                return ev; // start the handshaking process again
            }

            try {
                StateMachineEvent retev;

                if (type == RECVD_SYNACK && zi.getDestinationZephyrId() == ctx._loczid) {
                    ctx.setRemoteZid_(zi.getSourceZephyrId());
                    retev = finishhs(ctx, true);
                } else {
                    retev = ignorehs(ctx, zi);
                }

                return retev;
            } catch (ExAlreadyBound e) {
                return halt_(ctx, "handshake failed", e);
            }
        }
    },
    WAIT_FOR_HANDSHAKE_ACK
    {
        @Override
        public StateMachineEvent process_(StateMachineEvent ev, ZephyrClientContext ctx)
        {
            assertValidStateFunc(ctx, this);
            assertValidZephyrClientContext(ctx);

            IStateEventType type = ev.type();
            if (type == SENT_SYNACK) return PARK; // wait for ACK

            PBZephyrCandidateInfo zi = getSignallingData(ev);
            StateMachineEvent retev;

            if (type == RECVD_ACK && zi.getDestinationZephyrId() == ctx._loczid) {
                retev = finishhs(ctx, false);
            } else {
                retev = ignorehs(ctx, zi);
            }

            return retev;
        }
    },
    PREP_FOR_BINDING
    {
        @Override
        public StateMachineEvent process_(StateMachineEvent ev, ZephyrClientContext ctx)
        {
            assertValidStateFunc(ctx, this);
            assertValidZephyrClientContext(ctx);

            ZephyrClientContext.l.debug(ctx + ": create bind remzid:" + ctx._remzid);

            ctx.cleanCtrlFields_();
            Message.createBindMessage_(ctx._ctrlbuf, ctx._remzid);
            ctx._ctrlbuf.flip();

            return new StateMachineEvent(PREPARED_FOR_BINDING);
        }
    },
    BINDING
    {
        @Override
        public StateMachineEvent process_(StateMachineEvent ev, ZephyrClientContext ctx)
        {
            assertValidStateFunc(ctx, this);
            assertValidZephyrClientContext(ctx);

            assert (ctx._remzid != ZEPHYR_INVALID_CHAN_ID) : (ctx + ": invalid remzid");

            StateMachineEvent bindev = bindWithZephyr_(ctx);
            if (bindev.type() == BOUND) ctx._boss.remoteConnected_(ctx._remdid);

            return bindev;
        }
    },
    RECVING
    {
        @Override
        public StateMachineEvent process_(StateMachineEvent ev, ZephyrClientContext ctx)
        {
            assertValidStateFunc(ctx, this);
            assertValidZephyrClientContext(ctx);

            StateMachineEvent retev;

            if (ev.type() == PENDING_OUT_PACKET) {
                retev = ev;
            } else if (ev.type() == WRITE_COMPLETE) {
                retev = PARK; // yield
            } else {
                retev = recvPayload_(ctx);
            }

            return retev;
        }
    },
    SENDING_AND_RECVING
    {
        @Override
        public StateMachineEvent process_(StateMachineEvent ev, ZephyrClientContext ctx)
        {
            assertValidStateFunc(ctx, this);
            assertValidZephyrClientContext(ctx);

            StateMachineEvent recvev = recvPayload_(ctx);
            StateMachineEvent sendev = sendPayload_(ctx);

            StateMachineEvent retev;

            if (recvev == HALT || sendev == HALT) {
                retev = HALT;
            } else if (sendev.type() == WRITE_COMPLETE) {
                retev = sendev;
            } else {
                retev = PARK;
            }

            return retev;
        }
    },
    TERMINATED
    {
        @Override
        public StateMachineEvent process_(StateMachineEvent ev, ZephyrClientContext ctx)
        {
            assertValidStateFunc(ctx, this);
            assertValidZephyrClientContext(ctx);

            return HALT;
        }
    };

    @Override
    public String shortname_()
    {
        return name();
    }

    //
    // utility
    //

    /**
     * Asserts that we have a valid {@link ZephyrClientContext} object
     * @param ctx context object to check
     */
    private static void assertValidZephyrClientContext(ZephyrClientContext ctx)
    {
        assert ctx != null : ("null ctx");
    }

    /**
     * Asserts that the {@link ZephyrClientContext} object is in the correct state
     * for it to be processed by the state function
     * @param ctx context object from which to retrieve the current state
     * @param state state we expect to be in
     */
    private static void assertValidStateFunc(ZephyrClientContext ctx, IState<?> state)
    {
        assert ctx.curr_() == state:
            (ctx + ": invalid state:" + ctx.curr_()  + " for state func:" + state.shortname_());
    }

    /**
     * Utility function to HALT a state machine
     *
     * @param ctx  ZephyrClient object for which we want to halt state machine execution
     * @param haltmsg explanatory message indicating why we are HALTing
     * @param cause exception to be returned to the state-machine caller indicating
     * why we are HALTing
     * @return HALT
     */
    private static CoreEvent halt_(ZephyrClientContext ctx, String haltmsg, Exception cause)
    {
        ZephyrClientContext.l.warn(ctx + ": (HALT-INT) : " + haltmsg);
        ctx._haltex = cause;
        return HALT;
    }

    /**
     * Utility function to return PARK and at the same time enable interest
     * in OP_READ/OP_WRITE/OP_CONNECT/OP_ACCEPT for a {@link SelectionKey}
     *
     * @param ctx  ZephyrClient object for which we want to pause state machine
     * execution
     * @param ists What SelectionKey interests this object is interested in (i.e.
     * what i/o event would unpause the state machine)
     * @return PARK
     */
    private static CoreEvent park_(ZephyrClientContext ctx, int... ists)
    {
        ZephyrClientContext.l.debug(ctx + ": (PARK-INT) " + ctx.curr_());
        ZUtil.addInterest(ctx._k, ists);
        return PARK;
    }

    /**
     * Utility function for sending a BIND message to the Zephyr server
     * @param ctx context object for the ZephyrClient
     * @return HALT (if there was an error) or PARK (if the write could not complete
     * and we need to wait for the Selector to tell us that the socket is writable)
     *
     * <strong>re-registers for OP_WRITE prior to returning PARK</strong>
     */
    private static StateMachineEvent bindWithZephyr_(ZephyrClientContext ctx)
    {
        SocketChannel sc = ZUtil.getSocketChannel(ctx._k);
        try {
            sc.write(ctx._ctrlbuf);
            if (ctx._ctrlbuf.hasRemaining()) {
                return park_(ctx, SelectionKey.OP_WRITE);
            }
        } catch (IOException e) {
            ctx.cleanCtrlFields_();
            return halt_(ctx, "err while writing bind", e);
        }

        ctx.cleanCtrlFields_();
        return new StateMachineEvent(BOUND);
    }

    /**
     * Convenience method that ignores an incoming handshake message and parks the state machine
     *
     * @param ctx context object from which to retrieve the current state
     * @param zi object from which to retrieve the src and dst zids
     * @return a {@code CoreEvent} with type {@link PARK}
     */
    private static StateMachineEvent ignorehs(ZephyrClientContext ctx, PBZephyrCandidateInfo zi)
    {
        ZephyrClientContext.l.warn(ctx + ": ignore hs msg srczid:" +
            zi.getSourceZephyrId() + " dstzid:" + zi.getDestinationZephyrId());

        return PARK;
    }

    /**
     * Checks if the in-progress local handshake should proceed, or whether a remote-triggered
     * handshake should be given priority
     *
     * @param ctx context object from which to retrieve the current state
     * @param zi object from which to retrieve the src and dst zids
     * @return true if the remote-triggered handshake has higher priority, false if not
     */
    private static boolean shouldDefer(ZephyrClientContext ctx, PBZephyrCandidateInfo zi)
    {
        return zi.getSourceZephyrId() > ctx._loczid;
    }

    /**
     * Sends a valid {@code SYN} message to the remote peer
     *
     * @param ctx context object from which to retrieve the current state
     * @return an event of {@link SENT_SYN} type
     */
    private static StateMachineEvent synRemote(ZephyrClientContext ctx)
    {
        ctx.sendsyn_();
        return new StateMachineEvent(SENT_SYN);
    }

    /**
     * Sends a valid {@code SYNACK} message to the remote peer
     *
     * @param ctx context object from which to retrieve the current state
     * @return an event of {@link SENT_SYNACK} type
     */
    private static StateMachineEvent synackRemote(ZephyrClientContext ctx)
    {
        ctx.sendsynack_();
        return new StateMachineEvent(SENT_SYNACK);
    }

    /**
     * Finish a handshake; this may involve no external action (you received an ACK), or, it
     * may involve sending an ACK back (you received a SYNACK)
     *
     * @param ctx context object from which to retrieve the current state
     * @param sendack true if an ACK should be sent; false for no handshake messages to be sent
     * @return an event of {@link HANDSHAKE_COMPLETED} type
     */
    private static StateMachineEvent finishhs(ZephyrClientContext ctx, boolean sendack)
    {
        if (sendack) ctx.sendack_();

        ZephyrClientContext.l.info(ctx + ": handshake completed");

        return new StateMachineEvent(HANDSHAKE_COMPLETE);
    }

    /**
     * Extract the signalling parameters from an incoming {@code StateMachineEvent}
     *
     * @param ev {@code StateMachineEvent} from which to extract the signalling parameters
     * @return a valid {@link com.aerofs.proto.Transport.PBZephyrCandidateInfo} object
     */
    private static PBZephyrCandidateInfo getSignallingData(StateMachineEvent ev)
    {
        assert ev.data() != null : ("no signalling obj");
        PBZephyrCandidateInfo zi = (PBZephyrCandidateInfo) ev.data();
        return zi;
    }

    /**
     * Read from a {@link SocketChannel} into an array of {@link ByteBuffer}
     * objects (scattering read)
     *
     * @param ctx context object for the ZephyrClient for which we are doing reads
     * @param bufs ByteBuffer array to populate with the bytes we read
     * @return bytes >=0
     * @throws ExAbortState if bytes < 0
     */
    private static int read_(ZephyrClientContext ctx, ByteBuffer[] bufs)
        throws ExAbortState
    {
        int bytesin;
        try {
            SocketChannel sc = ZUtil.getSocketChannel(ctx._k);
            bytesin = (int) sc.read(bufs); // pktsize << MAX_INT
            if (bytesin < 0) {
                throw new ExAbortState("read channel hard close",
                    new IOException("end-of-stream from zephyr"));
            }
        } catch (IOException e) {
            throw new ExAbortState("err during read", e);
        }

        return bytesin;
    }

    /**
     * Read from a {@link SocketChannel} into a single {@link ByteBuffer}
     *
     * @param ctx context object for the ZephyrClient for which we are doing reads
     * @param b ByteBuffer to populate with the bytes we read
     * @return bytes >=0
     * @throws ExAbortState if bytes < 0
     */
    private static int read_(ZephyrClientContext ctx, ByteBuffer b)
        throws ExAbortState
    {
        return read_(ctx, new ByteBuffer[]{b});
    }

    /**
     * Utility function to read the assigned zid that is returned by the zephyr server when
     *
     * @param ctx context object for the ZephyrClient
     * @return true if the zid was read fully and the caller can proceed; false if not enough bytes
     * were read and the caller has to explicitly park
     * @throws ExAbortState if bytes < 0
     */
    private static boolean readzid(ZephyrClientContext ctx)
            throws ExAbortState
    {
        ByteBuffer b = ctx._ctrlbuf;
        b.limit(ZEPHYR_REG_MSG_LEN);

        int bytesin = read_(ctx, ctx._ctrlbuf);
        if (bytesin == 0) {
            return false;
        }

        if (b.position() < ZEPHYR_SERVER_HDR_LEN) {
            return false;
        }

        // by this point I have at least enough bytes for the header

        if (!ctx._ctrlhdrrcvd) {
            int bufpos = b.position();

            // IMPORTANT: i don't need to flip before reading here, since
            // I know for sure that all bytes I'll read are valid (see check
            // above). instead, I will simply set the position to 0 and read
            // only the magic and the length

            b.rewind();

            // magic

            byte[] m = new byte[ZEPHYR_MAGIC.length];
            b.get(m);
            if (!Arrays.equals(ZEPHYR_MAGIC, m)) {
                assert false : (ctx + ":expected reg"); // FIXME: remove
                throw new ExAbortState("expected reg msg", new ExBadMessage("expected reg"));
            }

            // length - now I know how many bytes past the header in the
            // buffer will be valid

            int len = b.getInt();
            if (len <= 0) {
                assert false : (ctx + ": invalid len"); // FIXME: remove
                throw new ExAbortState("bad len for reg msg", new ExBadMessage("invalid len"));
            }

            ctx._ctrlbodylen = len;
            ctx._ctrlhdrrcvd = true;

            // mark so that we know where the header ends
            assert b.position() == ZEPHYR_SERVER_HDR_LEN :
                    ("bad hdr exp:" + ZEPHYR_SERVER_HDR_LEN + " act:" + b.position());
            b.mark();

            // reset to where we were before we checked the header
            b.position(bufpos);
        }

        if (b.position() < ZEPHYR_REG_MSG_LEN) {
            return false;
        }

        // move to where the mark was (i.e. the end of the header)
        b.reset();

        // channel id

        int id = b.getInt();
        assert id != ZEPHYR_INVALID_CHAN_ID : (ctx + ": zephyr sent invalid id");
        ctx._loczid = id;

        // done
        return true;
    }

    /**
     * Utility function used when any state function wants to read payload
     * data from a remote ZephyrClient
     * @param ctx context object for the ZephyrClient
     * @return either HALT (if there was an error) or PARK (if you are waiting
     * for an external i/o event, i.e. READ event)
     *
     * IMPORTANT IMPLEMENTATION NOTE:
     * initially I intended to make the reads very general and allow splitting
     * headers and payloads across multiple ByteBuffer boundaries, allow multiple
     * packets in a single read, etc. Unfortunately the resulting code was very
     * complicated and (in my view) error-prone and hard to reason about. This
     * implementation may be slightly less performant (unclear how much) but is
     * much easier to reason about and should be less buggy
     *
     * <strong>Do not use for control buffers</strong>
     * <strong>re-registers for OP_READ prior to returning PARK</strong>
     */
    private static StateMachineEvent recvPayload_(ZephyrClientContext ctx)
    {
        try {
            while (true) {
                if (!ctx._k.isReadable()) { // we are always interested in read
                    return park_(ctx, SelectionKey.OP_READ);
                }

                if (!ctx._rdhdrrcvd) {
                    int bytesin = read_(ctx, ctx._rdhdrbuf);

                    ZephyrClientContext.l.debug(ctx + ": hdr b:" + bytesin +
                        " [" + ctx._rdhdrbuf.position() + "/" + ctx._rdhdrbuf.limit() + "]" +
                        " <- " + ctx._remdid);

                    if (ctx._rdhdrbuf.hasRemaining()) {
                        return park_(ctx, SelectionKey.OP_READ);
                    }

                    ctx._rdhdrbuf.flip();

                    // magic

                    int m = ctx._rdhdrbuf.getInt();
                    if (m != Param.CORE_MAGIC) {
                        String merrstr = "bad magic exp:" + Param.CORE_MAGIC + " act:" + m;
                        assert false : (ctx + ": " + merrstr); // FIXME: remove
                        throw new ExAbortState(merrstr, new ExBadMessage("bad frame:" + merrstr));
                    }

                    // payload length

                    int len = ctx._rdhdrbuf.getInt();
                    if (len <= 0) {
                        String lerrstr = "bad len:" + len;
                        assert false : (ctx + ": " + lerrstr); // FIXME: remove
                        throw new ExAbortState(lerrstr, new ExBadMessage("bad frame:" + lerrstr));
                    }

                    ctx._rdbodylen = len;
                    ctx._rdhdrrcvd = true;

                    assert ctx._rdbufs == null : (ctx + "rd buffers not cleaned up properly");
                    ctx._rdbufs = ZephyrClientUtil.setupBufferArray(ctx._boss, ctx._rdbodylen);
                }

                // if you're here, you are now reading the payload

                assert ctx._rdbufs != null : (ctx + ": null rd buffers");

                int bytesin = read_(ctx, ctx._rdbufs);

                // prints out the bytes received for this frame _including header_
                // NOTE: bytesin, _rdbodybytes, _rdbodylen only refer to the payload
                final int hdrlen = ctx._rdhdrbuf.limit();
                ZephyrClientContext.l.debug(ctx + ": b:" + bytesin +
                        " [" + (hdrlen + ctx._rdbodybytes) + "/" + (hdrlen + ctx._rdbodylen) +
                        " (pld:" + ctx._rdbodylen + ")]" +
                        " <- " + ctx._remdid);

                if (bytesin == 0) {
                    return park_(ctx, SelectionKey.OP_READ);
                }

                ctx._rdbodybytes += bytesin;
                ctx._bytesrx += bytesin;
                ctx._boss.addBytesRx_(bytesin);

                if (ctx._rdbodybytes < ctx._rdbodylen) {
                    return park_(ctx, SelectionKey.OP_READ);
                }

                // ok. now we've recv'd the full payload

                ZephyrClientContext.l.debug(ctx + ": b:fin <- " + ctx._remdid);

                for (ByteBuffer rdbuf : ctx._rdbufs) rdbuf.flip();
                ByteArrayInputStream bais = ZephyrClientUtil.createByteArrayInputStream(ctx._rdbufs);
                ctx.cleanRecvFields_();

                try {
                    // IMPORTANT: have to do this explicitly here because this
                    // is another exit-point out of this function which ends up
                    // calling the state-machine again. in certain circumstances
                    // this may result in OP_READ never being set
                    ZUtil.addInterest(ctx._k, SelectionKey.OP_READ);
                    ctx._boss.deliver_(ctx._remdid, bais, bais.available() + hdrlen);
                } catch (Exception e) {
                    throw new ExAbortState("err delivering packet to core", e);
                }
            }
        } catch (ExAbortState e) {
            ctx.cleanRecvFields_();
            return halt_(ctx, e.getAbortMsg(), e.getAbortException());
        }
    }

    /**
     * Utility function used when any state function wants to send payload
     * data to a remote ZephyrClient
     * @param ctx context object for the ZephyrClient
     * @return either HALT (if there was an error) or PARK (if you are waiting
     * for an external i/o event, i.e. WRITE event) or WRITE_COMPLETE if there are no packets
     * left to be written
     *
     * <strong>Do not use for control buffers</strong>
     * <strong>re-registers for OP_READ prior to returning PARK</strong>
     */
    private static StateMachineEvent sendPayload_(ZephyrClientContext ctx)
    {
        try {
            boolean wrotepkt = false;
            while (true) {
                if (wrotepkt || (ctx._wrcurrout == null && ctx._txq.isEmpty_())) {
                    return new StateMachineEvent(WRITE_COMPLETE);
                }

                if (ctx._wrcurrout == null) {
                    assert ctx._wrbufs == null :
                        (ctx + ": wr buffers not setup properly");

                    ctx._wrcurrout = ctx._txq.dequeue_();
                    ctx._wrbufs = ctx._wrcurrout.getWrappedByteArrays();

                    //
                    // use the chunk of code below if you don't want wrapped
                    // buffers:
                    //
                    // ctx._wrbufs = setupBufferArray(ctx._boss, ctx._wrcurrout._length);
                    // copyByteArraysToByteBuffers(ctx._wrcurrout._bss, ctx._wrbufs);
                    //
                    // make sure to set the returnToPool parameter to 'true'
                    // for cleanSendField
                    //
                }

                assert ctx._wrcurrout._length > 0 : ("invalid out len");

                 // keep this here so only happens if there's data waiting to go
                if (!ctx._k.isWritable()) {
                    return park_(ctx, SelectionKey.OP_WRITE);
                }

                // logRemaining(ctx._wrbufs, ctx + ": ", ZephyrClientContext.l);

                SocketChannel sc = ZUtil.getSocketChannel(ctx._k);
                int written;
                try {
                    written = (int) sc.write(ctx._wrbufs);
                    ctx._wrbodybytes += written;
                    ctx._bytestx += written;
                    ctx._boss.addBytesTx_(written);
                } catch (IOException e) {
                    throw new ExAbortState("err while wr", e);
                }

                ZephyrClientContext.l.debug(ctx + ": b:" + written +
                        " [" + ctx._wrbodybytes + "/" + ctx._wrcurrout._length + "]" +
                        " -> " + ctx._remdid);

                if (written == 0 /* nothing written in this iteration */ &&
                    ctx._wrbodybytes != ctx._wrcurrout._length) {
                    return park_(ctx, SelectionKey.OP_WRITE);
                } else if (ctx._wrbodybytes == ctx._wrcurrout._length) {
                    ZephyrClientContext.l.debug(ctx + ": b:fin -> " + ctx._remdid);
                    if (ctx._wrcurrout._waiter != null) ctx._wrcurrout._waiter.okay();
                    ctx.cleanSendFields_(false);
                    wrotepkt = true;
                }
            }
        } catch (ExAbortState e) {
            if (ctx._wrcurrout != null) {
                ZephyrClientUtil.handleError(ctx._remdid,
                    ctx._wrcurrout._waiter,
                    e.getAbortException(), ZephyrClientContext.l);
            }
            ctx.cleanSendFields_(false);
            return halt_(ctx, e.getAbortMsg(), e.getAbortException());
        }
    }
}
