/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp.multicast;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EIMaxcastMessage;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IMaxcast;
import com.aerofs.daemon.transport.lib.IStores;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.aerofs.daemon.transport.xmpp.XMPPUtilities;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence.Mode;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.google.common.base.Preconditions.checkArgument;

public final class Multicast implements IMaxcast, IStores, IXMPPConnectionServiceListener
{
    private static final Logger l = Loggers.getLogger(Multicast.class);

    private final FrequentDefectSender frequentDefectSender = new FrequentDefectSender();
    private final Map<SID, MultiUserChat> mucs = Maps.newTreeMap();
    private final Set<SID> allStores = Sets.newTreeSet();
    private final DID localdid;
    private final String xmppTransportId;
    private final String xmppServerDomain;
    private final MaxcastFilterReceiver maxcastFilterReceiver;
    private final XMPPConnectionService xmppConnectionService;
    private final ITransport transport;
    private final IBlockingPrioritizedEventSink<IEvent> outgoingEventSink;

    public Multicast(
            DID localDid,
            String xmppTransportId,
            String xmppServerDomain,
            MaxcastFilterReceiver maxcastFilterReceiver,
            XMPPConnectionService xmppConnectionService,
            ITransport transport,
            IBlockingPrioritizedEventSink<IEvent> outgoingEventSink)
    {
        this.localdid = localDid;
        this.xmppTransportId = xmppTransportId;
        this.xmppServerDomain = xmppServerDomain;
        this.maxcastFilterReceiver = maxcastFilterReceiver;
        this.xmppConnectionService = xmppConnectionService;
        this.transport = transport;
        this.outgoingEventSink = outgoingEventSink;
    }

    private void leaveMUC(SID sid) throws XMPPException
    {
        MultiUserChat muc;
        synchronized (this) {
            allStores.remove(sid);
            muc = mucs.remove(sid);
            if (muc == null) return;
        }

        muc.changeAvailabilityStatus("leaving", Mode.xa);
        muc.leave();
    }

    /**
     * join or create the muc if it doesn't exist yet. Note that we will
     * automatically subscribe the occupants' presence in the room once we have
     * joined. this class automatically re-join the rooms after xmpp
     * reconnection.
     */
    private MultiUserChat getMUC(SID sid) throws XMPPException
    {
        try {
            boolean create;
            MultiUserChat muc;
            synchronized (this) {
                allStores.add(sid);
                muc = mucs.get(sid);
                create = muc == null;
            }

            if (create) {
                // This has to be called to ensure that the connection is initialized (and thus the
                // smack static initializers have run) before using MultiUserChat, since
                // otherwise the MultiUserChat static initializer might deadlock with the
                // SmackConfiguration's static initializers.
                XMPPConnection conn = xmppConnectionService.conn();
                String roomName = JabberID.sid2muc(sid, xmppServerDomain);
                muc = new MultiUserChat(conn, roomName);
                joinRoom(muc);

                synchronized (this) {
                    mucs.put(sid, muc);
                }
            }

            return muc;
        } catch (IllegalStateException e) {
            throw new XMPPException(e);
        }
    }

    @Override
    public void sendPayload(SID sid, int mcastid, byte[] bs)
            throws XMPPException
    {
        try {
            OutArg<Integer> len = new OutArg<Integer>();
            getMUC(sid).sendMessage(XMPPUtilities.encodeBody(len, mcastid, bs));
            l.debug("send mc id:{} s:{}", mcastid, sid);
        } catch (IllegalStateException e) {
            throw new XMPPException(e);
        }
    }

    private void joinRoom(MultiUserChat muc) throws XMPPException
    {
        l.info("joining {}", shortenRoomStringForLogging(muc));

        // requesting no history
        DiscussionHistory history = new DiscussionHistory();
        history.setMaxChars(0);

        try {
            muc.join(JabberID.getMUCRoomNickname(localdid, xmppTransportId), null, history, SmackConfiguration.getPacketReplyTimeout());
            muc.addMessageListener(new PacketListener()
            {
                @Override
                public void processPacket(Packet packet)
                {
                    Message msg = (Message) packet;
                    if (msg.getBody() == null) {
                        l.warn("null-body msg from " + packet.getFrom());

                    } else {
                        try {
                            recvMessage(msg);
                        } catch (Exception e) {
                            frequentDefectSender.logSendAsync("process mc from " + msg.getFrom() + ": " + XMPPUtilities.getBodyDigest(msg.getBody()), e);
                        }
                    }
                }
            });
        } catch (IllegalStateException e) {
            throw new XMPPException(e);
        }

        l.info("joined");
    }

    private String shortenRoomStringForLogging(MultiUserChat muc)
    {
        String room = muc.getRoom();
        int at = room.indexOf('@');
        return at == -1 ? room : room.substring(0, at);
    }

    private void recvMessage(Message msg) throws IOException, ExFormatError,
            ExNoResource
    {
        String[] tokens = JabberID.tokenize(msg.getFrom());
        DID did = JabberID.jid2did(tokens, xmppServerDomain);
        if (did.equals(localdid)) return;

        checkArgument(JabberID.isMUCAddress(tokens, xmppServerDomain));

        l.debug("{} recv mc", did);

        OutArg<Integer> wirelen = new OutArg<Integer>();
        byte [] bs = XMPPUtilities.decodeBody(did, wirelen, msg.getBody(), maxcastFilterReceiver);

        // A null byte stream is returned if the packet is to be filtered away
        if (bs == null) return;

        Endpoint ep = new Endpoint(transport, did);

        ByteArrayInputStream is = new ByteArrayInputStream(bs);
        recvMessage(ep, is, wirelen.get());
    }

    private void recvMessage(Endpoint ep, ByteArrayInputStream is, int wirelen)
            throws IOException, ExNoResource
    {
        // NOTE: Assume that ep.did() != localdid as this is checked in recvMessage(Message msg)
        outgoingEventSink.enqueueThrows(new EIMaxcastMessage(ep, is, wirelen), Prio.LO);
    }

    @Override
    public synchronized void xmppServerDisconnected()
    {
        mucs.clear();
    }

    @Override
    public void xmppServerConnected(XMPPConnection conn) throws XMPPException
    {
        // re-register all existing rooms. a copy of the allStores array is needed
        // as it may get modified by getMUC().
        // a loop is needed in case more entries are added to "allStores" but failed
        // to register on the server while we're working
        Set<SID> all = null;
        while (true) {
            synchronized (this) {
                if (all != null && all.equals(allStores)) break;
                all = new TreeSet<SID>(allStores);
            }

            for (SID sid : all) getMUC(sid);
        }
    }

    @Override
    public void updateStores(SID[] sidsAdded, SID[] sidsRemoved)
    {
        for (SID sid : sidsAdded) {
            try {
                getMUC(sid);
            } catch (Exception e) {
                // the chat room will be re-subscribed when reconnected
                l.warn("postpone muc 4 " + sid + ": " + e);
            }
        }

        for (SID sid : sidsRemoved) {
            try {
                leaveMUC(sid);
            } catch (Exception e) {
                // the chat room will be re-subscribed when reconnected
                l.warn("leave muc 4 " + sid + ", ignored: " + Util.e(e));
            }
        }
    }
}
