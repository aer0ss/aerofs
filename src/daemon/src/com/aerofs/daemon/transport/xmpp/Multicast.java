package com.aerofs.daemon.transport.xmpp;

import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EIMaxcastMessage;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.transport.lib.IMaxcast;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;

import org.apache.log4j.Logger;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

public class Multicast implements IMaxcast
{
    private static final Logger l = Util.l(Multicast.class);

    private final XMPP x;

    private final Map<SID, MultiUserChat> _mucs = new TreeMap<SID, MultiUserChat>();

    private final Set<SID> _all = new TreeSet<SID>();

    Multicast(XMPP x)
    {
        this.x = x;
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
    public MultiUserChat getMUC(SID sid) throws XMPPException
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
                String name = ID.sid2muc(sid);
                muc = new MultiUserChat(x.cw().conn(), name);

                try {
                    l.info("gri:" + name);
                    MultiUserChat.getRoomInfo(x.cw().conn(), name);
                } catch (XMPPException e) {
                    if (e.getXMPPError() != null
                            && e.getXMPPError().getCode() == 404) {
                        l.info("muc " + name + " not exists. create now.");
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
            getMUC(sid).sendMessage(XMPP.encodeBody(len, mcastid, bs));
            //_bytesOut += len.get();
        } catch (IllegalStateException e) {
            throw new XMPPException(e);
        }
    }

    private final FrequentDefectSender _fds = new FrequentDefectSender();

    private void joinRoom(MultiUserChat muc) throws XMPPException
    {
        l.info("joining " + muc.getRoom());

        // requesting no history
        DiscussionHistory history = new DiscussionHistory();
        history.setMaxChars(0);

        try {
            muc.join(ID.did2user(Cfg.did()), null, history,
                SmackConfiguration.getPacketReplyTimeout());

            muc.addMessageListener(new PacketListener() {
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
                                msg.getFrom() + ": " + XUtil.getBodyDigest(
                                        msg.getBody()), e);
                        }
                    }
                }
            });

        } catch (IllegalStateException e) {
            throw new XMPPException(e);
        }

        l.info("room joined");
    }

    private void createRoom(MultiUserChat muc) throws XMPPException
    {
        l.info("creating " + muc.getRoom());

        try {
            muc.create(ID.did2user(Cfg.did()));

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

        l.info("room created");
    }

    private void recvMessage(Message msg) throws IOException, ExFormatError,
            ExNoResource
    {
        String[] tokens = ID.tokenize(msg.getFrom());
        DID did = ID.jid2did(tokens);
        if (did.equals(Cfg.did())) return;

        assert ID.isMUCAddress(tokens);

        OutArg<Integer> wirelen = new OutArg<Integer>();
        byte [] bs = x.decodeBody(did, wirelen, msg.getBody());

        // A null byte stream is returned if the packet is to be filtered away
        if (bs == null) return;

        Endpoint ep = new Endpoint(x, did);

        ByteArrayInputStream is = new ByteArrayInputStream(bs);
        recvMessage(ep, ID.muc2sid(tokens[0]), is, wirelen.get());
    }

    private void recvMessage(Endpoint ep, SID sid, ByteArrayInputStream is,
                            int wirelen)
            throws IOException, ExNoResource
    {
        // NOTE: Assume that ep.did() != Cfg.did(),
        // as this is checked in recvMessage(Message msg)
        //if (ep.did().equals(Cfg.did())) return;
        x.sink().enqueueThrows(new EIMaxcastMessage(ep, sid, is, wirelen),
            Prio.LO);
    }

    public synchronized void xmppServerDisconnected()
    {
        _mucs.clear();
    }

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
