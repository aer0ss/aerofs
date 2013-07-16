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
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
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

import static com.aerofs.daemon.transport.xmpp.XMPPUtilities.decodeBody;
import static com.aerofs.daemon.transport.xmpp.XMPPUtilities.encodeBody;
import static com.aerofs.daemon.transport.xmpp.XMPPUtilities.getBodyDigest;
import static com.google.common.base.Preconditions.checkArgument;

public final class Multicast implements IMaxcast
{
    private static final Logger l = Loggers.getLogger(Multicast.class);

    private final Map<SID, MultiUserChat> _mucs = new TreeMap<SID, MultiUserChat>();

    private final Set<SID> all = new TreeSet<SID>();
    private final ITransport transport;
    private final XMPPConnectionService _xmppConnectionService;
    private final IBlockingPrioritizedEventSink<IEvent> sink;
    private final DID localdid;
    private final String xmppTransportId;
    private final MaxcastFilterReceiver maxcastFilterReceiver;

    // FIXME (AG): the endpoint for multicast systems should be itself, not zephyr, not xmpp, not anything
    public Multicast(ITransport transport, MaxcastFilterReceiver maxcastFilterReceiver, XMPPConnectionService xmppConnectionService, IBlockingPrioritizedEventSink<IEvent> sink, DID localdid, String xmppTransportId)
    {
        this.transport = transport;
        this.maxcastFilterReceiver = maxcastFilterReceiver;
        this._xmppConnectionService = xmppConnectionService;
        this.sink = sink;
        this.localdid = localdid;
        this.xmppTransportId = xmppTransportId;
    }

    private void leaveMUC(SID sid) throws XMPPException
    {
        MultiUserChat muc;
        synchronized (this) {
            all.remove(sid);
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
                all.add(sid);
                muc = _mucs.get(sid);
                create = muc == null;
            }

            if (create) {
                String roomName = JabberID.sid2muc(sid);
                // This has to be called to ensure that the connection is initialized (and thus the
                // smack static initializers have run) before using MultiUserChat, since
                // otherwise the MultiUserChat static initializer might deadlock with the
                // SmackConfiguration's static initializers.

                /*
                "expo.retry.xsct" daemon prio=5 tid=7fe824277000 nid=0x116644000 in Object.wait() [116642000]
                   java.lang.Thread.State: RUNNABLE
                    at java.lang.Class.forName0(Native Method)
                    at java.lang.Class.forName(Class.java:169)
                    at org.jivesoftware.smack.SmackConfiguration.parseClassToLoad(SmackConfiguration.java:306)
                    at org.jivesoftware.smack.SmackConfiguration.<clinit>(SmackConfiguration.java:86)
                    at org.jivesoftware.smack.Connection.<clinit>(Connection.java:118)
                    at org.jivesoftware.smack.ConnectionConfiguration.<init>(ConnectionConfiguration.java:71)
                    at com.aerofs.daemon.transport.xmpp.XMPPConnectionService.newConnection(XMPPConnectionService.java:102)
                    at com.aerofs.daemon.transport.xmpp.XMPPConnectionService.connectImpl_(XMPPConnectionService.java:178)
                    at com.aerofs.daemon.transport.xmpp.XMPPConnectionService.connect_(XMPPConnectionService.java:164)
                    at com.aerofs.daemon.transport.xmpp.XMPPConnectionService.access$2(XMPPConnectionService.java:161)
                    at com.aerofs.daemon.transport.xmpp.XMPPConnectionService$1.call(XMPPConnectionService.java:137)
                    - locked <7f8443f18> (a com.aerofs.daemon.transport.xmpp.XMPPConnectionService)
                    at com.aerofs.daemon.transport.xmpp.XMPPConnectionService$1.call(XMPPConnectionService.java:1)
                    at com.aerofs.lib.Util.exponentialRetry(Util.java:1201)
                    at com.aerofs.lib.Util$4.run(Util.java:1188)
                    at java.lang.Thread.run(Thread.java:680)

                "x" daemon prio=5 tid=7fe8242c2800 nid=0x116135000 in Object.wait() [116134000]
                   java.lang.Thread.State: RUNNABLE
                    at org.jivesoftware.smackx.muc.MultiUserChat.<clinit>(MultiUserChat.java:109)
                    at com.aerofs.daemon.transport.xmpp.Multicast.getMUC(Multicast.java:79)
                    at com.aerofs.daemon.transport.xmpp.Multicast.updateStores_(Multicast.java:241)
                    at com.aerofs.daemon.transport.jingle.XMPP.updateStores_(XMPP.java:321)
                    at com.aerofs.daemon.transport.lib.HdUpdateStores.handle_(HdUpdateStores.java:19)
                    at com.aerofs.daemon.transport.lib.HdUpdateStores.handle_(HdUpdateStores.java:1)
                    at com.aerofs.daemon.event.lib.EventDispatcher.dispatch_(EventDispatcher.java:39)
                    at com.aerofs.daemon.transport.jingle.XMPP$1.run(XMPP.java:187)
                    at java.lang.Thread.run(Thread.java:680)
                 */

                XMPPConnection conn = _xmppConnectionService.conn();
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
            getMUC(sid).sendMessage(encodeBody(len, mcastid, bs));
            l.debug("send mc id:{} s:{}", mcastid, sid);
        } catch (IllegalStateException e) {
            throw new XMPPException(e);
        }
    }

    private final FrequentDefectSender _fds = new FrequentDefectSender();

    private void joinRoom(MultiUserChat muc) throws XMPPException
    {
        l.info("joining {}", shortenRoomStringForLogging(muc));

        // requesting no history
        DiscussionHistory history = new DiscussionHistory();
        history.setMaxChars(0);

        try {
            muc.join(JabberID.getMUCRoomNickname(localdid, xmppTransportId),
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
                            _fds.logSendAsync("process_ mc from " + msg.getFrom() + ": " + getBodyDigest(
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
            muc.create(JabberID.getMUCRoomNickname(localdid, xmppTransportId));

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
        if (did.equals(localdid)) return;

        checkArgument(JabberID.isMUCAddress(tokens));

        l.debug("recv mc d:{}", did);

        OutArg<Integer> wirelen = new OutArg<Integer>();
        byte [] bs = decodeBody(did, wirelen, msg.getBody(), maxcastFilterReceiver);

        // A null byte stream is returned if the packet is to be filtered away
        if (bs == null) return;

        Endpoint ep = new Endpoint(transport, did);

        ByteArrayInputStream is = new ByteArrayInputStream(bs);
        recvMessage(ep, is, wirelen.get());
    }

    private void recvMessage(Endpoint ep, ByteArrayInputStream is, int wirelen)
            throws IOException, ExNoResource
    {
        sink.enqueueThrows(new EIMaxcastMessage(ep, is, wirelen), Prio.LO);
    }

    public synchronized void xmppServerDisconnected()
    {
        _mucs.clear();
    }

    public void xmppServerConnected(XMPPConnection conn) throws XMPPException
    {
        // re-register all existing rooms. a copy of the all array is needed
        // as it may get modified by getMUC().
        // a loop is needed in case more entries are added to "all" but failed
        // to register on the server while we're working
        Set<SID> all = null;
        while (true) {
            synchronized (this) {
                if (all != null && all.equals(this.all)) break;
                all = new TreeSet<SID>(this.all);
            }

            for (SID sid : all) getMUC(sid);
        }
    }

    public void updateStores_(SID[] sidsAdded, SID[] sidsRemoved)
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
