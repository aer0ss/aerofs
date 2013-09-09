/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp;

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
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence.Mode;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.google.common.base.Preconditions.checkArgument;

public class Multicast implements IMaxcast, IStores, IXMPPConnectionServiceListener
{
    private static final Logger l = Loggers.getLogger(Multicast.class);

    private final Map<SID, MultiUserChat> _mucs = new TreeMap<SID, MultiUserChat>();
    private final FrequentDefectSender _fds = new FrequentDefectSender();
    private final Set<SID> _all = new TreeSet<SID>();
    private final DID _localDid;
    private final String _xmppTransportId;
    private final String _xmppServerDomain;
    private final MaxcastFilterReceiver _mcfr;
    private final XMPPConnectionService _xsc;
    private final ITransport _tp;
    private final IBlockingPrioritizedEventSink<IEvent> _sink;

    public Multicast(
            DID localDid,
            String xmppTransportId,
            String xmppServerDomain,
            MaxcastFilterReceiver mcfr,
            XMPPConnectionService xsc,
            ITransport tp,
            IBlockingPrioritizedEventSink<IEvent> sink)
    {
        _localDid = localDid;
        _xmppTransportId = xmppTransportId;
        _xmppServerDomain = xmppServerDomain;
        _mcfr = mcfr;
        _xsc = xsc;
        _tp = tp;
        _sink = sink;
    }

    private void leaveMUC(SID sid) throws XMPPException
    {
        MultiUserChat muc;
        synchronized (this) {
            _all.remove(sid);
            muc = _mucs.remove(sid);
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
                _all.add(sid);
                muc = _mucs.get(sid);
                create = muc == null;
            }

            if (create) {
                String roomName = JabberID.sid2muc(sid, _xmppServerDomain);
                // This has to be called to ensure that the connection is initialized (and thus the
                // smack static initializers have run) before using MultiUserChat, since
                // otherwise the MultiUserChat static initializer might deadlock with the
                // SmackConfiguration's static initializers.
                XMPPConnection conn = _xsc.conn();
                muc = new MultiUserChat(conn, roomName);

                try {
                    l.info("gri {}", sid);
                    MultiUserChat.getRoomInfo(conn, roomName);
                } catch (XMPPException e) {
                    if (e.getXMPPError() != null && e.getXMPPError().getCode() == 404) {
                        l.info("muc " + roomName + " not exists. create now.");
                        createRoom(muc);
                    } else {
                        l.error(e.getMessage());
                        throw e;
                    }
                }

                joinRoom(muc);

                synchronized (this) {
                    _mucs.put(sid, muc);
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
            muc.join(JabberID.getMUCRoomNickname(_localDid, _xmppTransportId),
                    null, history, SmackConfiguration.getPacketReplyTimeout());
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
                            _fds.logSendAsync("process_ mc from " +
                                    msg.getFrom() + ": " + XMPPUtilities.getBodyDigest(
                                    msg.getBody()), e);
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

    private void createRoom(MultiUserChat muc) throws XMPPException
    {
        l.info("creating " + muc.getRoom());

        try {
            muc.create(JabberID.getMUCRoomNickname(_localDid, _xmppTransportId));

            // create an instant room using the server's default configuration
            // see: http://www.igniterealtime.org/builds/smack/docs/latest/documentation/extensions/muc.html
            // the xmpp server should be set up with the following defaults:
            // muc#roomconfig_enablelogging: false
            // muc#roomconfig_persistentroom: true
            // muc#roomconfig_maxusers: unlimited
            muc.sendConfigurationForm(new Form(Form.TYPE_SUBMIT));
        } catch (IllegalStateException e) {
            throw new XMPPException(e);
        }

        l.info("created");
    }

    private void recvMessage(Message msg) throws IOException, ExFormatError,
            ExNoResource
    {
        String[] tokens = JabberID.tokenize(msg.getFrom());
        DID did = JabberID.jid2did(tokens);
        if (did.equals(_localDid)) return;

        checkArgument(JabberID.isMUCAddress(tokens));

        l.debug("recv mc d:{}", did);

        OutArg<Integer> wirelen = new OutArg<Integer>();
        byte [] bs = XMPPUtilities.decodeBody(did, wirelen, msg.getBody(), _mcfr);

        // A null byte stream is returned if the packet is to be filtered away
        if (bs == null) return;

        Endpoint ep = new Endpoint(_tp, did);

        ByteArrayInputStream is = new ByteArrayInputStream(bs);
        recvMessage(ep, is, wirelen.get());
    }

    private void recvMessage(Endpoint ep, ByteArrayInputStream is, int wirelen)
            throws IOException, ExNoResource
    {
        // NOTE: Assume that ep.did() != _localDid as this is checked in recvMessage(Message msg)
        _sink.enqueueThrows(new EIMaxcastMessage(ep, is, wirelen), Prio.LO);
    }

    @Override
    public synchronized void xmppServerDisconnected()
    {
        _mucs.clear();
    }

    @Override
    public void xmppServerConnected(XMPPConnection conn) throws XMPPException
    {
        // re-register all existing rooms. a copy of the _all array is needed
        // as it may get modified by getMUC().
        // a loop is needed in case more entries are added to "_all" but failed
        // to register on the server while we're working
        Set<SID> all = null;
        while (true) {
            synchronized (this) {
                if (all != null && all.equals(_all)) break;
                all = new TreeSet<SID>(_all);
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
