/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp.multicast;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.ids.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.ids.SID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EIMaxcastMessage;
import com.aerofs.daemon.transport.lib.IMaxcast;
import com.aerofs.daemon.transport.lib.IPresenceSource;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.aerofs.daemon.transport.xmpp.XMPPUtilities;
import com.aerofs.defects.Defect;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence.Mode;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.aerofs.daemon.transport.xmpp.XMPPUtilities.getBodyDigest;
import static com.aerofs.defects.Defects.newFrequentDefect;
import static com.google.common.base.Preconditions.checkArgument;

// FIXME: This class has potential for bad state mismatches with XMPPConnectionService (and it's
// own callers who may do things like updateStores() before we are finished handling a connect
// notifier). Refactor this so its lifetime is scoped to a particular xmpp connection?
public final class XMPPMulticast implements IMaxcast, IPresenceSource, IXMPPConnectionServiceListener
{
    private static final Logger l = Loggers.getLogger(XMPPMulticast.class);

    private final Defect defect = newFrequentDefect("transport.multicast");
    private final Map<SID, MultiUserChat> mucs = Maps.newTreeMap();
    private final Set<SID> allStores = Sets.newTreeSet();
    private final DID localdid;
    private final String xmppServerDomain;
    private final MaxcastFilterReceiver maxcastFilterReceiver;
    private final XMPPConnectionService xmppConnectionService;
    private final IBlockingPrioritizedEventSink<IEvent> outgoingEventSink;

    public XMPPMulticast(
            DID localDid,
            String xmppServerDomain,
            MaxcastFilterReceiver maxcastFilterReceiver,
            XMPPConnectionService xmppConnectionService,
            IBlockingPrioritizedEventSink<IEvent> outgoingEventSink)
    {
        this.localdid = localDid;
        this.xmppServerDomain = xmppServerDomain;
        this.maxcastFilterReceiver = maxcastFilterReceiver;
        this.xmppConnectionService = xmppConnectionService;
        this.outgoingEventSink = outgoingEventSink;
    }

    private void leaveMUC(SID sid) throws XMPPException
    {
        l.info("leaving muc {}", sid.toStringFormal());
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
    {
        try {
            OutArg<Integer> len = new OutArg<>();
            getMUC(sid).sendMessage(XMPPUtilities.encodeBody(len, mcastid, bs));
            l.debug("send mc id:{} s:{}", mcastid, sid);
        } catch (Exception e) {
            l.warn("mc {} {}", sid, mcastid, BaseLogUtil.suppress(e));
        }
    }

    private void joinRoom(MultiUserChat muc) throws XMPPException
    {
        l.info("joining muc {}", shortenRoomStringForLogging(muc));

        // requesting no history
        DiscussionHistory history = new DiscussionHistory();
        history.setMaxChars(0);

        try {
            // TODO: remove transport id from JID
            muc.join(JabberID.getMUCRoomNickname(localdid, "z"), null, history, SmackConfiguration.getPacketReplyTimeout());
            muc.addMessageListener(packet -> {
                Message msg = (Message) packet;
                if (msg.getBody() == null) {
                    l.warn("null-body msg from " + packet.getFrom());

                } else {
                    try {
                        recvMessage(msg);
                    } catch (Exception e) {
                        defect.setMessage("process mc from " + msg.getFrom() + ": "
                                + getBodyDigest(msg.getBody()))
                                .setException(e)
                                .sendAsync();
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

    private void recvMessage(Message msg) throws IOException, ExInvalidID,
            ExNoResource
    {
        String[] tokens = JabberID.tokenize(msg.getFrom());
        DID did = JabberID.jid2did(tokens, xmppServerDomain);
        if (did.equals(localdid)) return;

        checkArgument(JabberID.isMUCAddress(tokens, xmppServerDomain));

        l.debug("{} recv mc", did);

        OutArg<Integer> wirelen = new OutArg<>();
        byte [] bs = XMPPUtilities.decodeBody(did, wirelen, msg.getBody(), maxcastFilterReceiver);

        // A null byte stream is returned if the packet is to be filtered away
        if (bs == null) return;

        // TODO: mock transport for maxcast messages?
        Endpoint ep = new Endpoint(null, did);

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
        l.info("xmppConn {}", mucs.size());
        // re-register all existing rooms. a copy of the allStores array is needed
        // as it may get modified by getMUC().
        // a loop is needed in case more entries are added to "allStores" but failed
        // to register on the server while we're working
        Set<SID> all = null;
        while (true) {
            synchronized (this) {
                if (all != null && all.equals(allStores)) break;
                all = new TreeSet<>(allStores);
            }

            for (SID sid : all) getMUC(sid);
        }
    }

    @Override
    public void updateInterest(SID[] sidsAdded, SID[] sidsRemoved)
    {
        for (SID sid : sidsAdded) {
            try {
                getMUC(sid);
            } catch (Exception e) {
                // the chat room will be re-subscribed when reconnected
                l.warn("postpone muc 4 {}:", sid, e);
            }
        }

        for (SID sid : sidsRemoved) {
            try {
                leaveMUC(sid);
            } catch (Exception e) {
                // the chat room will be re-subscribed when reconnected
                l.warn("leave muc 4 {}, ignore: {}", sid, Util.e(e));
            }
        }
    }
}
