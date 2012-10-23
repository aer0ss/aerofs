package com.aerofs.daemon.core.net.dtls;

import com.aerofs.daemon.core.net.*;
import com.aerofs.daemon.core.net.dtls.DTLSMessage.Type;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.IDumpStatMisc;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.PrioQueue;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExDTLS;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.injectable.InjectableFile;

import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

public class DTLSLayer implements IDuplexLayer, IDumpStatMisc
{
    private static final Logger l = Util.l(DTLSLayer.class);

    static enum Footer
    {
        IN_OLD,
        OUT_NEW,
        OUT_OLD,
        HS_REQ;

        static final int SIZE = 1;

        public byte toByte()
        {
            return (byte) ordinal();
        }
    }

    private final DTLSCache _send;
    private final DTLSCache _recv;

    private IUnicastInputLayer _upper;
    private IUnicastOutputLayer _lower;

    private final Factory _f;

    public static class Factory
    {
        private final DTLSMessage.Factory<ByteArrayInputStream> _factMsgBIS;
        private final DTLSMessage.Factory<byte[]> _factMsgBA;
        private final DID2User _d2u;
        private final TC _tc;
        private final DTLSCache.Factory _factCache;
        private final InjectableFile.Factory _factFile;

        @Inject
        public Factory(TC tc, DID2User d2u,
                DTLSMessage.Factory<byte[]> factMsgBA,
                DTLSMessage.Factory<ByteArrayInputStream> factMsgBIS,
                DTLSCache.Factory factCache, InjectableFile.Factory factFile)
        {
            _tc = tc;
            _d2u = d2u;
            _factMsgBA = factMsgBA;
            _factMsgBIS = factMsgBIS;
            _factCache = factCache;
            _factFile = factFile;
        }

        public DTLSLayer create_() throws IOException
        {
            return new DTLSLayer(this);
        }
    }

    private DTLSLayer(Factory fact)
            throws IOException
    {
        _f = fact;

        USERID_MSG = _f._factMsgBA.create_(Type.SEND_UNICAST, USERID_BYTES);

        // because the SSL library can't handle non-ASCII paths properly,
        // (in particular SSL_CTX_load_verify_locations() can't read non-ASCII paths,
        // see http://stackoverflow.com/questions/2401059/openssl-with-unicode-paths)
        // we copy the certificate files to elsewhere if the path is non-ASCII

        String pathCACert = Util.join(AppRoot.abs(), C.CA_CERT);
        if (!Util.isASCII(AppRoot.abs())) {
            InjectableFile fTmp = _f._factFile.createTempFile("caa", "cert");
            _f._factFile.create(pathCACert).copy(fTmp, false, false);
            fTmp.deleteOnExit();
            pathCACert = fTmp.getPath();
        }

        String pathDevCert = Util.join(Cfg.absRTRoot(), C.DEVICE_CERT);
        if (!Util.isASCII(Cfg.absRTRoot())) {
            InjectableFile f = _f._factFile.createTempFile("dev", "cert");
            _f._factFile.create(pathDevCert).copy(f, false, false);
            f.deleteOnExit();
            pathDevCert = f.getPath();
        }

        _send = _f._factCache.create_(this, true, pathCACert, pathDevCert);
        _recv = _f._factCache.create_(this, false, pathCACert, pathDevCert);
    }

    @Override
    public void onUnicastDatagramReceived_(RawMessage r, PeerContext pc)
    {
        if (l.isDebugEnabled()) l.debug("onUnicastDatagramReceived " + pc);

        DTLSMessage<ByteArrayInputStream> dtlsMessage =
                _f._factMsgBIS.create_(Type.UNICAST_RECV, r._is);

        processRecvdMsg_(dtlsMessage, r._wirelen, pc);
    }

    @Override
    public void onStreamBegun_(StreamID streamId, RawMessage r, PeerContext pc)
    {
        if (l.isDebugEnabled()) l.debug("onStreamBegun " + streamId + " " + pc);

        DTLSMessage<ByteArrayInputStream> dtlsMessage =
                _f._factMsgBIS.create_(Type.STREAM_BEGUN, r._is, streamId, 0);

        processRecvdMsg_(dtlsMessage, r._wirelen, pc);
    }

    @Override
    public void onStreamChunkReceived_(StreamID streamId, int seq, RawMessage r, PeerContext pc)
    {
        if (l.isDebugEnabled()) l.debug("onStreamChunkReceived " + streamId + " " + seq + " " + pc);

        DTLSMessage<ByteArrayInputStream> dtlsMessage =
                _f._factMsgBIS.create_(Type.CHUNK_RECV, r._is, streamId, seq);

        processRecvdMsg_(dtlsMessage, r._wirelen, pc);
    }

    @Override
    public void onStreamAborted_(StreamID streamId, Endpoint ep, InvalidationReason reason)
    {
        if (l.isDebugEnabled()) l.debug("onStreamAborted " + ep + " " + streamId + " " + reason);

        _upper.onStreamAborted_(streamId, ep, reason);
    }

    @Override
    public void sessionEnded_(Endpoint ep, boolean outbound, boolean inbound)
    {
        if (l.isDebugEnabled()) l.debug("sessionEnded " + ep + " inbound " + inbound);
        _upper.sessionEnded_(ep, outbound, inbound);

        // TODO discard all contexts belonging to this session. Note: be very
        // careful about this due to priority inversions. See
        // UnicastInputTopLayer.sessionEnded().
    }

    @Override
    public void sendUnicastDatagram_(byte[] bs, PeerContext pc)
            throws Exception
    {
        if (l.isDebugEnabled()) l.debug("sendUnicastDatagram " + pc);

        DTLSMessage<byte[]> msg = _f._factMsgBA.create_(Type.SEND_UNICAST, bs);

        processSendMsg_(msg, pc);
    }

    @Override
    public void beginOutgoingStream_(StreamID streamId, byte[] bs, PeerContext pc, Token tk)
            throws Exception
    {
        if (l.isDebugEnabled()) l.debug("beginOutgoingStream " + streamId.toString() + " " + pc);

        DTLSMessage<byte[]> msg = _f._factMsgBA.create_(Type.BEGIN_STREAM, bs, streamId, 0, tk);

        processSendMsg_(msg, pc);

        msg.wait_();
    }

    @Override
    public void sendOutgoingStreamChunk_(StreamID streamId, int seq, byte[] bs, PeerContext pc, Token tk)
            throws Exception
    {
        if (l.isDebugEnabled()) l.debug("sendOutgoingStreamChunk " + streamId + " " + seq + " " + pc);

        DTLSMessage<byte[]> msg = _f._factMsgBA.create_(Type.SEND_CHUNK, bs, streamId, seq, tk);

        processSendMsg_(msg, pc);

        msg.wait_();
    }

    @Override
    public void endOutgoingStream_(StreamID streamId, PeerContext pc)
            throws ExNoResource, ExAborted
    {
        if (l.isDebugEnabled()) l.debug("endOutgoingStream " + streamId + " " + pc);

        _lower.endOutgoingStream_(streamId, pc);
    }

    @Override
    public void abortOutgoingStream_(StreamID streamId, InvalidationReason reason, PeerContext pc)
            throws ExNoResource, ExAborted
    {
        if (l.isDebugEnabled()) l.debug("abortOutgoingStream " + streamId + " " + pc);

        _lower.abortOutgoingStream_(streamId, reason, pc);
    }

    @Override
    public void init_(IUnicastInputLayer upper, IUnicastOutputLayer lower)
    {
        _upper = upper;
        _lower = lower;
    }

    IUnicastOutputLayer lower()
    {
        return _lower;
    }

    @Override
    public void endIncomingStream_(StreamID streamId, PeerContext pc)
            throws ExNoResource, ExAborted
    {
        if (l.isDebugEnabled()) l.debug("endIncomingStream " + streamId + " " + pc);

        _lower.endIncomingStream_(streamId, pc);
    }

    private void deliverOrVerifyUser_(DTLSEntry entry,
            DTLSMessage<ByteArrayInputStream> msg,
            int wirelen, PeerContext pc, ByteArrayInputStream is,
            boolean sender)
            throws ExDTLS
    {
        if (entry._user != null) {
            l.debug("deliver msg " + is.available());
            pc.setUser(entry._user);
            sendToUpperLayer_(msg._type, msg._sid, msg._seq, is, wirelen, pc);

        } else {
            l.debug("verify user");
            try {
                for (byte b : USERID_MAGIC_BYTES) {
                    if (b != is.read()) {
                        throw new ExDTLS(pc.did() + " userid magic mismatch");
                    }
                }

                byte[] bs = new byte[is.available()];
                new DataInputStream(is).readFully(bs);

                String user = Util.utf2string(bs);

                // verify cname
                String pcn = entry.getPeerCName();
                String expected = SecUtil.getCertificateCName(user, pc.did());
                if (!expected.equals(pcn)) {
                    l.warn(pcn + " != " + user + " + " + pc.did().toStringFormal());
                    throw new ExDTLS("cname verification");
                }

                _f._d2u.processMappingFromPeer_(pc.did(), user);

                if (sender) {
                    _send.promote_(pc.ep(), entry);

                } else {
                    _recv.promote_(pc.ep(), entry);

                    // the sender already send the userid message at the time of
                    // engine creation
                    byte[] encryptedBS = entry.encrypt(USERID_BYTES, pc, Footer.IN_OLD, null);
                    assert encryptedBS != null;

                    _lower.sendUnicastDatagram_(encryptedBS, pc);
                }

                // should be the last step after all other steps succeed
                entry._user = user;

            } catch (Exception e) {
                throw new ExDTLS("error during verification: " + Util.e(e));
            }
        }
    }

    // alternatively, pass origMsgLen in...
    private void processRecvdMsg_(DTLSMessage<ByteArrayInputStream> msg, int wirelen,
            PeerContext pc)
    {
        Byte footer = null;
        boolean delivered = false;

        try {
            ByteArrayInputStream is = msg._msg;
            byte[] input = new byte[is.available() - Footer.SIZE]; // remove footer
            int available = is.available();
            Util.verify(is.available() - Footer.SIZE == is.read(input));

            footer = (byte) is.read();

            assert is.available() == 0 : is.available();

            // make sure the footer didn't get corrupted
            assert footer >= 0 && footer < Footer.values().length :
                    (pc + " " + available + " " + footer + " " + Footer.values().length + " :: " +
                     msg._type + " " + msg._sid + " " + msg._seq + " " + msg._tk);

            Footer f = Footer.values()[footer];
            l.debug("recv " + f);

            switch (f) {
                case OUT_NEW: {
                    // overwrite any engine in the backlog. it is necessary to
                    // remove stalled engines in the backlog due to sender restarts
                    // during handshaking
                    //
                    // TODO vulnerable to DoS attacks. Use engine numbers to prevent
                    // it (see dtls.docx)
                    //
                    _recv.removeEntryInBacklog_(pc.ep());

                    DTLSEntry entry = _recv.createEntry_(pc.ep());

                    // Should return null bytestream
                    OutArg<Boolean> hsSent = new OutArg<Boolean>(false);
                    Util.verify(null == entry.decrypt_(input, pc, Footer.IN_OLD, hsSent));
                    assert hsSent.get();

                    break;
                }
                case OUT_OLD: {
                    ArrayList<DTLSEntry> entries = _recv.findEntryList_(pc.ep());

                    if (entries.isEmpty()) {
                        l.debug("svr: send HS_REQ");

                        /*
                         * XXX TODO: This is (maybe???) subject to a DoS attack
                         * in the following manner:
                         * A malicious user can inject a byte into the server's
                         * stream with
                         * HANDSHAKE_REQ. The client will see this message and
                         * initiate a much
                         * costlier (size wise) re-negotiation with the server
                         */
                        byte[] out = new byte[Footer.SIZE];
                        out[0] = Footer.HS_REQ.toByte();

                        _lower.sendUnicastDatagram_(out, pc);

                    } else {
                        l.debug("svr: found entries for cli");

                        for (DTLSEntry entry : entries) {

                            OutArg<Boolean> hsSent = new OutArg<Boolean>(false);

                            ByteArrayInputStream isToDeliver = entry.decrypt_(input, pc,
                                    Footer.IN_OLD, hsSent);


                            //if handshake message was sent through decrypt()
                            if (hsSent.get()) {

                                //The receiver channel assumes that the sender channel has the same (or smaller)
                                //timeout value as us. This means that even if a handshake message was sent
                                //if the timeout has elapsed, the client with the sender channel has already removed
                                //us from the backlog. That means we should also remove this entry from the backlog
                                //to allow the client to re-negotiate as necessary
                                _recv.timeoutDTLSInHandShake(entry, pc.ep(), true);

                                // if the decrypt() command generated a message
                                // directly to the lower layer, no need to check
                                // the other engine
                                break;
                            }

                            if (null != isToDeliver) {
                                deliverOrVerifyUser_(entry, msg, wirelen, pc, isToDeliver, false);
                                delivered = true;
                                // if we successfully decrypted, no need
                                // to iterate over the rest of the entries
                                break;
                            }
                        }
                    }
                    break;
                }

                case IN_OLD: {
                    /*
                     * There's technically a case when we receive an IN_OLD
                     * message, and we find a
                     * a DTLSEntry that matches the server's PeerContext, but
                     * for some reason
                     * it doesn't actually work (i.e. let us decrypt the
                     * message)... I'm not sure
                     * how this case can be produced though.
                     */
                    ArrayList<DTLSEntry> entries = _send.findEntryList_(pc.ep());
                    if (entries.isEmpty()) {
                        // Client must have died and restarted
                        l.debug("cli: can't find entry matching svr's PC, drop");

                    } else {
                        for (DTLSEntry entry : entries) {

                            OutArg<Boolean> hsSent = new OutArg<Boolean>(false);

                            ByteArrayInputStream isToDeliver = entry.decrypt_(input, pc,
                                    Footer.OUT_OLD, hsSent);

                            //hanshake message sent through decrypt();
                            if (hsSent.get()) {

                                //it's possible that even though we've sent a response on the send channel,
                                //the corresponding client with the recv channel has timed us out.
                                //here we assume that both sender and receiver have similar timeouts
                                //and so it is safe to remove this entry from the backlog if the timeout has passed
                                _send.timeoutDTLSInHandShake(entry, pc.ep(), true); //send because of IN_OLD
                                break;
                            }

                            if (null != isToDeliver) {
                                deliverOrVerifyUser_(entry, msg, wirelen, pc, isToDeliver, true);
                                delivered = true;
                                // if we successfully decrypted, no need
                                // to iterate over the rest of the entries
                                break;

                            } else {
                                l.debug("no stream dec'ed");
                            }

                            // process_ queued up messages to be encrypted
                            _send.drainAndSendEnqueuedMessages_(entry, pc);
                        }
                    }
                    break;
                }

                case HS_REQ:
                    kickOffHandShaking_(pc);
                    break;
            }

        } catch (ExDTLS e) {
            l.warn("recv err: " + Util.e(e));

            assert footer != null;

            switch (Footer.values()[footer]) {
                case OUT_NEW:
                case OUT_OLD:
                    l.debug("rm eng from recvCache");
                    _recv.removeEntries_(pc.ep());
                    break;
                case IN_OLD:
                    l.debug("rm eng from sendCache");
                    _send.removeEntries_(pc.ep());
                    break;
                case HS_REQ:
                    break;
            }

        } catch (Exception e) {
            l.warn("recv error: " + Util.e(e));

        } finally {
            if (!delivered && msg._sid != null) {
                l.warn("stream not dec'ed. abort");
                // notify upper layers
                onStreamAborted_(msg._sid, pc.ep(), InvalidationReason.DTLS_ERROR);
                // notify lower layers
                try {
                    endIncomingStream_(msg._sid, pc);
                } catch (Exception e1) {
                    l.error("can't end stream. TODO handle it: " + Util.e(e1));
                }
            }
        }
    }

    static final byte[] USERID_MAGIC_BYTES = {0x23, 0x45, 0x67};
    static final byte[] USERID_BYTES =
            Util.concatenate(USERID_MAGIC_BYTES, Util.string2utf(Cfg.user()));

    private final DTLSMessage<byte[]> USERID_MSG;

    // this is used only to kick off handshake, never sent over the wire
    private static final byte[] HS_KICKOFF_BYTES = {0x37, 0x37, 0x37, 0x37};

    private DTLSEntry kickOffHandShaking_(PeerContext pc) throws Exception
    {
        DTLSEntry entry = _send.createEntry_(pc.ep());

        // send the user id message first. it must be the highest priority
        // so that it's always sent before any other message.
        entry._sendQ.enqueue_(USERID_MSG, Prio.HI);

        try {
            l.debug("send " + Footer.OUT_NEW);
            byte[] encryptedBS = entry.encrypt(HS_KICKOFF_BYTES, pc,
                    Footer.OUT_NEW, null);
            assert encryptedBS == null; // should not return anything here
        } catch (ExDTLS e) {
            l.debug("remove entry from sendCache");
            _send.removeEntries_(pc.ep());
            throw e;
        }

        return entry;
    }

    private void processSendMsg_(DTLSMessage<byte[]> msg, PeerContext pc)
            throws Exception
    {
        ArrayList<DTLSEntry> entries = _send.findEntryList_(pc.ep());

        // if we can't find a DTLSEntry,
        // then this is a new session for an OUT context
        if (entries.isEmpty()) {
            DTLSEntry entry = kickOffHandShaking_(pc);

            // We only start the Profiler for the first message to kick off a handshake
            msg.getProfiler().start();

            l.debug("cli: enqueue msg for later");
            entry._sendQ.enqueue_(msg, _f._tc.prio());

        } else {
            l.debug("send " + Footer.OUT_OLD);

            for (DTLSEntry entry : entries) {
                PrioQueue<DTLSMessage<byte[]>> sendQ = entry._sendQ;

                byte[] bs = msg._msg;
                if (sendQ.isEmpty_()) {
                    l.debug("q empty");

                    OutArg<Boolean> hsSent = new OutArg<Boolean>(false);

                    try {
                        // you *must* encrypt using every entry, as there's
                        // no guarantee that the first entry will correspond to
                        // an active context on the receiver's side
                        //
                        // this is a safe operation because it is impossible for
                        // both the client and the server to have two
                        // DTLSEntries at the same time, so each message is
                        // guaranteed to be decrypted at most once.

                        byte[] bsToSend = entry.encrypt(bs, pc, Footer.OUT_OLD,
                                hsSent);

                        if (null != bsToSend) {
                            assert !hsSent.get();

                            _send.promote_(pc.ep(), entry);

                            sendToLowerLayer_(msg, bsToSend, pc);

                        } else {

                            l.debug("cli: enqueue msg for later");
                            sendQ.enqueue_(msg, _f._tc.prio());

                            // handshake has been sent through this engine,
                            // so there's no need to try the other engine.
                            // this must be done *after* the message is enqueued
                            if (hsSent.get()) {

                                //need to timeout the dtls entry if the timeout time has passed
                                //this assumes that both the client and the server have similar timeout values

                                _send.timeoutDTLSInHandShake(entry, pc.ep(), true);
                                break;
                            } else {
                                l.debug("not in hs, !hsSent && !bsToSend");
                            }
                        }

                    } catch (ExDTLS e) {
                        l.debug("remove eng from sendCache");
                        _send.removeEntries_(pc.ep());
                        throw e;
                    }

                } else if (!sendQ.isFull_()) {
                    l.debug("cli: eng q not full, enq msg");
                    sendQ.enqueue_(msg, _f._tc.prio());
                    _send.drainAndSendEnqueuedMessages_(entry, pc);

                } else {
                    throw new ExNoResource("CLI: eng q full, drop pkt");
                }
            }
        }
    }

    private void sendToUpperLayer_(Type type, StreamID sid, int seq,
            ByteArrayInputStream is, int wirelen, PeerContext pc)
    {
        RawMessage r = new RawMessage(is, wirelen);

        switch (type) {
            case UNICAST_RECV:
                _upper.onUnicastDatagramReceived_(r, pc);
                break;
            case STREAM_BEGUN:
                _upper.onStreamBegun_(sid, r, pc);
                break;
            default:
                assert type == Type.CHUNK_RECV;
                _upper.onStreamChunkReceived_(sid, seq, r, pc);
        }
    }

    @SuppressWarnings("fallthrough")
    void sendToLowerLayer_(DTLSMessage<byte[]> msg, byte[] bs, PeerContext pc)
            throws Exception
    {
        assert bs != null;

        try {
            switch (msg._type) {
                case SEND_UNICAST:
                    _lower.sendUnicastDatagram_(bs, pc);
                    break;
                case BEGIN_STREAM:
                    if (!msg.isBeginStreamSent()) {
                        // set the bit *before* calling the lower layer, so that
                        // if the lower layer failed, subsequent calls to
                        // sendToLowerLayer_ on the same message won't begin a new
                        // stream, but instead throws stream-not-found exception
                        msg.setBeginStreamSent();
                        _lower.beginOutgoingStream_(msg._sid, bs, pc, msg._tk);
                        break;
                    } else {
                        // continue to the 'case' below
                    }
                default:
                    assert msg._type == Type.SEND_CHUNK ||
                            msg._type == Type.BEGIN_STREAM;
                    _lower.sendOutgoingStreamChunk_(msg._sid, msg._seq, bs, pc, msg._tk);
            }

            // If there're two engines for the pc, the caller of this method may
            // try encrypt and call this method on both engines. We set the
            // message to be okay if ANY of the engines has successfully pushed
            // out the message. One reason of doing this to prevent DoS attack
            // where the attacker may block stream processing by initiating a
            // new but stalled connection to a device.
            msg.done_(null);

        } catch (Exception e) {
            msg.done_(e);
            throw e;
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + "sendCache");
        _send.dumpStatMisc(indent + indentUnit, indentUnit, ps);
        ps.println(indent + "recvCache");
        _recv.dumpStatMisc(indent + indentUnit, indentUnit, ps);
    }
}
