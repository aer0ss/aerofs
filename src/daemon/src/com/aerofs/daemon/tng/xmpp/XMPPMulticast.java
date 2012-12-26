/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.IMaxcastListener;
import com.aerofs.daemon.tng.ReceivedMaxcastFilter;
import com.aerofs.daemon.tng.base.IMaxcastService;
import com.aerofs.daemon.tng.xmpp.XMPPServerConnectionService.IXMPPServerConnectionListener;
import com.aerofs.base.Base64;
import com.aerofs.lib.C;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.base.id.SID;
import com.aerofs.lib.notifier.IListenerVisitor;
import com.aerofs.lib.notifier.Notifier;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.Builder;
import com.aerofs.proto.Transport.PBTPHeader;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.log4j.Logger;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.MultiUserChat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.daemon.lib.DaemonParam.MAX_TRANSPORT_MESSAGE_SIZE;
import static com.aerofs.daemon.tng.xmpp.ID.did2user;
import static com.aerofs.daemon.tng.xmpp.ID.isMUCAddress;
import static com.aerofs.daemon.tng.xmpp.ID.jid2did;
import static com.aerofs.daemon.tng.xmpp.ID.sid2muc;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static org.jivesoftware.smack.packet.Message.Type.chat;
import static org.jivesoftware.smack.packet.Message.Type.error;
import static org.jivesoftware.smack.packet.Message.Type.groupchat;
import static org.jivesoftware.smack.packet.Message.Type.headline;
import static org.jivesoftware.smack.packet.Message.Type.normal;

final class XMPPMulticast
        implements IMaxcastService, ISignallingService, IXMPPServerConnectionListener
{
    private static final Logger l = Util.l(XMPPMulticast.class);

    private final static int MAXCAST_UNFILTERED = -1;
    private static final int HEADER_LEN = (Integer.SIZE / Byte.SIZE) * 2 + 1;
    private static final UncancellableFuture<ImmutableSet<DID>> MUOD_FUTURE; // statically initalized

    private final ISingleThreadedPrioritizedExecutor _executor;
    private final XMPPServerConnectionService _xmppConnectionService;
    private final ReceivedMaxcastFilter _receivedMaxcastFilter;
    private final FrequentDefectSender _frequentDefectSender;
    private final DID _localDID;
    private final Notifier<IMaxcastListener> _maxcastNotifier = Notifier.create();
    private final AtomicBoolean _started = new AtomicBoolean(false);
    private final Map<SID, MultiUserChat> _mucRooms = new TreeMap<SID, MultiUserChat>();
    private final Set<SID> _allInterestedStores = new TreeSet<SID>();
    private final Map<ISignallingClient, Predicate<SignallingMessage>> _signallingClients = new HashMap<ISignallingClient, Predicate<SignallingMessage>>();

    static {
        MUOD_FUTURE = UncancellableFuture.createSucceeded(ImmutableSet.<DID>of());
    }

    static XMPPMulticast getInstance_(ISingleThreadedPrioritizedExecutor executor,
            XMPPServerConnectionService xmppServerConnectionService,
            ILinkStateService networkLinkStateService,
            ReceivedMaxcastFilter receivedMaxcastFilter, FrequentDefectSender frequentDefectSender,
            DID localdid)
    {
        XMPPMulticast maxcastService = new XMPPMulticast(executor, xmppServerConnectionService,
                receivedMaxcastFilter, frequentDefectSender, localdid);

        xmppServerConnectionService.addListener_(maxcastService);
        networkLinkStateService.addListener_(maxcastService, sameThreadExecutor());

        return maxcastService;
    }

    private XMPPMulticast(ISingleThreadedPrioritizedExecutor executor,
            XMPPServerConnectionService xmppConnectionService,
            ReceivedMaxcastFilter receivedMaxcastFilter, FrequentDefectSender frequentDefectSender,
            DID localDID)
    {
        this._executor = executor;
        this._xmppConnectionService = xmppConnectionService;
        this._receivedMaxcastFilter = receivedMaxcastFilter;
        this._frequentDefectSender = frequentDefectSender;
        this._localDID = localDID;
    }

    @Override
    public void start_()
    {
        boolean previouslyStarted = _started.getAndSet(true);
        if (previouslyStarted) return;

        l.info("start: start xmpp server connection service");

        _xmppConnectionService.start_();
    }

    @Override
    public void onLinkStateChanged_(ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed, ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> previous)
    {
        l.info("lsc: noop");
    }

    @Override
    public ListenableFuture<ImmutableSet<DID>> getMaxcastUnreachableOnlineDevices_()
    {
        return MUOD_FUTURE;
    }

    @Override
    public void registerSignallingClient_(final ISignallingClient signallingClient,
            final Predicate<SignallingMessage> predicate)
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                Util.verify(_signallingClients.put(signallingClient, predicate) == null);
            }
        });
    }

    @Override
    public void deregisterSignallingClient_(final ISignallingClient signallingClient)
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                _signallingClients.remove(signallingClient);
            }
        });
    }

    @Override
    public ListenableFuture<Void> sendSignallingMessage_(final SignallingMessage message)
    {
        l.info("send signalling hdr:" + message.message + " to did:" + message.did);

        final UncancellableFuture<Void> returned = UncancellableFuture.create();

        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    Message xmpp = new Message(ID.did2jid(message.did, false), normal);
                    xmpp.setBody(encodeBody(message.message.toByteArray()));

                    _xmppConnectionService.sendMessage(xmpp);

                    returned.set(null);
                } catch (Exception e) {
                    returned.setException(e);
                }
            }
        });

        return returned;
    }

    /**
     * Processes an encoded XMPPBasedTransportFactory message from a peer.
     *
     * @param msg XMPPBasedTransportFactory message to decode and processIncoming_
     * @throws ExFormatError if the JID cannot be converted to a DID
     * @throws ExProtocolError if the header is an unrecognized (i.e. unhandled) type {@link
     * ISignallingClient} due to resource constraints
     */
    private void processReceivedMessage_(Message msg)
            throws ExFormatError, ExProtocolError
    {
        DID did;
        byte[] decoded;
        PBTPHeader hdr;
        try {
            OutArg<Integer> wirelen = new OutArg<Integer>(0);
            did = jid2did(msg.getFrom());
            decoded = decodeBody(did, wirelen, msg.getBody(), null);
            hdr = PBTPHeader.parseFrom(decoded);
        } catch (IOException e) {
            l.warn("err while decoding xmpp msg:" + e);
            return;
        }

        if (decoded == null) return;

        PBTPHeader.Type type = hdr.getType();
        l.info("rcv msg type:" + hdr.getType().name());

        SignallingMessage signallingMessage = new SignallingMessage(did, hdr);
        List<ISignallingClient> eligibleClients = new LinkedList<ISignallingClient>();

        for (Entry<ISignallingClient, Predicate<SignallingMessage>> entry : _signallingClients.entrySet()) {
            if (entry.getValue().apply(signallingMessage)) {
                // The predicate set for this ISignallingClient passed, so add this ISignallingClient
                // as an eligible client
                eligibleClients.add(entry.getKey());
            }
        }

        if (eligibleClients.isEmpty()) {
            // No one wants to handle this message
            throw new ExProtocolError(type.getClass());
        }

        // Right now, we only allow one ISignallingClient to process a message
        assert eligibleClients.size() == 1;

        for (ISignallingClient client : eligibleClients) {
            client.processSignallingMessage_(signallingMessage);
        }
    }

    @Override
    public void xmppServerConnected(final XMPPConnection smackConnection)
            throws XMPPException
    {
        l.info("adding standard packet processor for conn:" + smackConnection);

        // this _has_ to run in the context of the smack thread

        smackConnection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(final Packet packet)
            {
                assert packet instanceof Message : packet.getClass();

                final Message m = (Message) packet;
                Message.Type t = m.getType();

                assert t != groupchat && t != headline && t != chat;

                assert t != error;

                _executor.execute(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try {
                            processReceivedMessage_(m);
                        } catch (ExFormatError e) {
                            l.warn("pl: cannot convert JID to DID from:" +
                                    packet.getFrom() + " err: " + Util.e(e));
                        } catch (ExProtocolError e) {
                            l.warn("pl: unrecognized message from:" +
                                    packet.getFrom() + " err:" + Util.e(e));
                        } catch (Exception e) {
                            l.error("pl: cannot processIncoming_ valid message from:" +
                                    packet.getFrom() + " err:" + Util.e(e));

                            // we fatal for a number of reasons here:
                            // - an exception from disconnect within a subsystem
                            //   is unrecoverable. It means that we're trying
                            //   to recover from an error condition, but our
                            //   recovery processIncoming_ is failing. in this case we
                            //   really shouldn't go on
                            // - we cannot reschedule this event because ordering
                            //   is extremely important in processing XMPPBasedTransportFactory
                            //   messages. Receiving online, offline messages
                            //   is very different than offline, online

                            SystemUtil.fatal(e);
                        }
                    }
                });
            }
        }, new MessageTypeFilter(normal));

        // run the rest in the context of our executor

        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    l.info("registering stores");

                    for (SID sid : _allInterestedStores) {
                        makeAndGetStore_(sid);
                    }
                } catch (XMPPException e) {
                    l.warn("failed to handle XMPP server login");
                }

                for (ISignallingClient c : _signallingClients.keySet()) {
                    c.signallingChannelConnected_();
                }
            }
        });
    }

    @Override
    public void xmppServerDisconnected()
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                _mucRooms.clear();

                for (ISignallingClient c : _signallingClients.keySet()) {
                    c.signallingChannelDisconnected_();
                }
            }
        });
    }

    @Override
    public void addListener_(IMaxcastListener listener, Executor notificationExecutor)
    {
        _maxcastNotifier.addListener(listener, notificationExecutor);
    }

    @Override
    // runs in the transport thread
    public ListenableFuture<Void> sendDatagram_(int maxcastId, SID sid, byte[] payload, Prio pri)
    {
        UncancellableFuture<Void> f = UncancellableFuture.create();

        try {
            makeAndGetStore_(sid).sendMessage(encodeBody(maxcastId, payload));
            f.set(null);
        } catch (Exception e) {
            f.setException(e);
        }

        return f;
    }

    @Override
    // runs in the transport thread
    public ListenableFuture<Void> updateLocalStoreInterest_(ImmutableSet<SID> added,
            ImmutableSet<SID> removed)
    {
        UncancellableFuture<Void> f = UncancellableFuture.create();

        boolean wasFailure = false;

        for (SID sid : added) {
            try {
                makeAndGetStore_(sid);
            } catch (Exception e) {
                // chatroom will be re-subscribed to when we reconnect // FIXME: is this true?
                l.warn("sid:" + sid + " resubscribe on reconnection err:" + e);
                wasFailure = true;
            }
        }

        for (SID sid : removed) {
            try {
                leaveStore_(sid);
            } catch (Exception e) {
                l.warn("leave muc 4 " + sid + ", ignored: " +
                        Util.e(e)); // chatroom will be left from when we reconnect
                wasFailure = true;
            }
        }

        if (wasFailure) {
            f.setException(new Exception("not all store interests updated"));
        } else {
            f.set(null);
        }

        return f;
    }

    private void leaveStore_(SID sid)
            throws XMPPException
    {
        _allInterestedStores.remove(sid);
        MultiUserChat muc = _mucRooms.remove(sid);
        if (muc == null) return;

        _xmppConnectionService.leaveRoom(muc);
    }

    /**
     * join or create_ the muc if it doesn't exist yet. Note that we will automatically subscribe
     * the occupants' presence in the room once we have joined. this class automatically re-join the
     * rooms after xmpp reconnection.
     */
    private MultiUserChat makeAndGetStore_(final SID sid)
            throws XMPPException
    {
        try {
            _allInterestedStores.add(sid);

            MultiUserChat muc = _mucRooms.get(sid);
            boolean create = muc == null;

            if (create) {
                l.info("sid:" + sid + " does not exist - creating");

                muc = _xmppConnectionService.makeAndJoinMUC(did2user(_localDID), sid2muc(sid));

                l.info("sid:" + sid + " adding packet processor");

                muc.addMessageListener(new PacketListener()
                {
                    @Override
                    public void processPacket(Packet packet)
                    {
                        final Message msg = (Message) packet;

                        if (msg.getBody() == null) {
                            l.warn("null-body msg from " + packet.getFrom());
                        } else {
                            _executor.execute(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    try {
                                        processReceivedMUCMessage_(msg);
                                    } catch (Exception e) {
                                        String errmsg = "sid:" + sid + " processing msg from:" +
                                                msg.getFrom() + ":" +
                                                getBodyDigest(msg.getBody()) +
                                                " err:" + e;

                                        l.warn(errmsg);

                                        _frequentDefectSender.logSendAsync(errmsg);
                                    }
                                }
                            });

                        }
                    }
                });

                _mucRooms.put(sid, muc);
            }

            return muc;
        } catch (IllegalStateException e) {
            l.warn("sid:" + sid + " failed to create and join");
            throw new XMPPException(e);
        }
    }

    private void processReceivedMUCMessage_(Message msg)
            throws IOException, ExFormatError
    {
        final String[] tokens = ID.tokenize(msg.getFrom());
        final DID did = ID.jid2did(tokens);
        final SID sid = ID.muc2sid(tokens[0]);

        if (did.equals(_localDID)) {
            return;
        }

        assert isMUCAddress(tokens);

        OutArg<Integer> wlen = new OutArg<Integer>();
        final byte[] bs = decodeBody(did, wlen, msg.getBody(), _receivedMaxcastFilter);
        final int wirelen = wlen.get();

        // A null byte stream is returned if the packet is to be filtered away
        if (bs == null) return;

        _maxcastNotifier.notifyOnOtherThreads(new IListenerVisitor<IMaxcastListener>()
        {
            @Override
            public void visit(IMaxcastListener listener)
            {
                listener.onMaxcastDatagramReceived(did, sid, new ByteArrayInputStream(bs), wirelen);
            }
        });
    }

    /**
     * Decode the body of an incoming XMPPBasedTransportFactory message
     *
     * @param did {@link com.aerofs.base.id.DID} of the remote peer from whom the message was
     * received
     * @param wirelen will be populated with the number of bytes the message took up on the wire
     * @param body the encoded body of the XMPPBasedTransportFactory message
     * @return null if magic number doesn't match or it's a duplicate, or a decoded message body
     *         otherwise
     * @throws IOException if the message cannot be decoded
     */
    private byte[] decodeBody(DID did, OutArg<Integer> wirelen, String body,
            ReceivedMaxcastFilter rmcf)
            throws IOException
    {
        ByteArrayInputStream bos = new ByteArrayInputStream(body.getBytes());
        DataInputStream is = new DataInputStream(new Base64.InputStream(bos));
        try {
            int magic = is.readInt();
            if (magic != C.CORE_MAGIC) {
                l.info("magic mismatch from " + did + ": " + body);
                return null;
            }

            // Parse the maxcast id.
            // Do not attempt to filter away if it is an UNFILTERED packet
            int mcastid = is.readInt();
            if (MAXCAST_UNFILTERED != mcastid && rmcf.isRedundant(did, mcastid)) return null;

            int len = is.readInt();
            if (len <= 0 || len > MAX_TRANSPORT_MESSAGE_SIZE) {
                throw new IOException("insane msg len " + len);
            }

            byte[] bs = new byte[len];
            try {
                is.readFully(bs);

                int read = is.read();
                if (read == -1) throw new IOException("chksum not present");

                byte chksum = (byte) read;
                for (byte b : bs) chksum ^= b;
                if (chksum != 0) throw new IOException("chksum mismatch");

            } catch (EOFException e) {
                throw new IOException("msg len " + len + " > actual");
            }

            if (bos.available() != 0) {
                throw new IOException("msg len " + len + " < avail by " +
                        bos.available());
            }

            wirelen.set(len + HEADER_LEN);
            return bs;
        } finally {
            if (is != null) is.close();
        }
    }

    @Override
    public void dumpStat(PBDumpStat template, Builder bd)
            throws Exception
    {
        // FIXME: implement
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        // FIXME: implement
    }

    /**
     * Wrapper for encodeBody for subsystems that do not use Maxcast filtering e.g.,
     * ZephyrClientManager
     *
     * @param bss bytes to encode
     * @return encoded string ready for transport over the XMPPBasedTransportFactory channel
     */
    private static String encodeBody(byte[]... bss)
    {
        return encodeBody(MAXCAST_UNFILTERED, bss);
    }

    private static String encodeBody(int mcastid, byte[]... bss)
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            DataOutputStream os = new DataOutputStream(new Base64.OutputStream(bos));

            os.writeInt(C.CORE_MAGIC);

            // TODO consider adding mcastid to chksum?
            // if so, don't forget to check in decodeBody
            os.writeInt(mcastid);

            int len = 0;
            for (byte[] bs : bss) len += bs.length;
            os.writeInt(len);

            byte chksum = 0;
            for (byte[] bs : bss) {
                for (byte b : bs) chksum ^= b;
                os.write(bs);
            }

            os.write(chksum);

            os.close();
        } catch (IOException e) {
            SystemUtil.fatal(e);
        }

        return bos.toString();
    }

    /**
     * Helper method to print a digest of an encoded XMPPBasedTransportFactory message
     *
     * @param body of which to print the digest
     * @return digest of the body
     */
    private static String getBodyDigest(String body)
    {
        if (body.length() <= 68) return body;
        else return body.substring(0, 64) + "...";
    }
}
