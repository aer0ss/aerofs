/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.IBlockingPrioritizedEventSink;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.transport.lib.INetworkStats.BasicStatsCounter;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientManager;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;

import java.io.IOException;
import java.util.Map;

import static com.aerofs.daemon.transport.xmpp.ID.did2FormAJid;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.newHashMap;
import static org.jivesoftware.smack.packet.Message.Type.chat;
import static org.jivesoftware.smack.packet.Message.Type.error;
import static org.jivesoftware.smack.packet.Message.Type.groupchat;
import static org.jivesoftware.smack.packet.Message.Type.headline;

public class Zephyr extends XMPP implements ISignallingChannel
{
    public Zephyr(DID localdid, String id, int rank, IBlockingPrioritizedEventSink<IEvent> sink, MaxcastFilterReceiver mcfr)
    {
        super(localdid, id, rank, sink, mcfr);
        ZephyrClientManager zcm = new ZephyrClientManager(id, rank, this, new BasicStatsCounter(), this);
        setPipe_(zcm);
    }

    @Override
    public boolean supportsMulticast()
    {
        return false;
    }

    //--------------------------------------------------------------------------
    //
    //
    // ISignallingChannel methods (how subsystems can send/receive messages via
    // a control channel)
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public void registerSignallingClient_(PBTPHeader.Type type, ISignallingClient ccc)
    {
        checkState(!ready(), "z: already started");
        checkState(!_processors.containsKey(type), "z: existing ccc for type:" + type.name());

        _processors.put(type, ccc);
    }

    @Override
    public void sendMessageOnSignallingChannel(
            final DID did, final PBTPHeader msg, final ISignallingClient ccc)
    {
        assertNonDispThread();

        enqueueIntoXmpp(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                OutArg<Integer> len = new OutArg<Integer>(0);
                String enc = XMPP.encodeBody(len, msg.toByteArray());

                final Message xmsg = new Message(did2FormAJid(did, id()), Message.Type.normal);
                xmsg.setBody(enc);

                // for now we actually don't have to enqueue the sending task as a new
                // event since sendPacket() is synchronized, but it's easy to do so if we
                // have to for whatever reason (i.e. I've tried both approaches and this
                // signature supports both). This allows us to implement this method
                // however we wish, with whatever synchronization style we want

                try {
                    xmppServerConnection().conn().sendPacket(xmsg);
                } catch (XMPPException e) {
                    notifySignallingClientOfError(e);
                } catch (IllegalStateException e) {
                    // NOTE: this can happen because smack considers it illegal to attempt to send
                    // a packet if the channel is not connected. Since we may be notified of a
                    // disconnection after actually enqueuing the packet to be sent, it's entirely
                    // possible for this to occur

                    notifySignallingClientOfError(e);
                }
            }

            private void notifySignallingClientOfError(Exception e)
            {
                try {
                    ccc.sendSignallingMessageFailed_(did, msg, e);
                } catch (ExNoResource e2) {
                    l.error("x: failed to handle error while sending packet");
                    SystemUtil.fatal("shutdown due to err:" + e2 + " triggered by err:" + e);
                }
            }
        }, Prio.HI);
    }

    @Override
    public void xmppServerConnected(final XMPPConnection conn) throws XMPPException
    {
        assertNonDispThread();

        conn.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(final Packet packet)
            {
                enqueueIntoXmpp(new AbstractEBSelfHandling()
                {
                    @Override
                    public void handle_()
                    {
                        if (packet instanceof Message) {
                            // for now all non-presence packets are directed to Zephyr
                            // clients only

                            Message m = (Message) packet;
                            if (m.getSubject() != null) return;

                            Message.Type t = m.getType();
                            checkArgument(t != groupchat && t != headline && t != chat,
                                "pl: groupchat, headline and chat messages are not expected here");
                            checkArgument(t != error,
                                "pl: errors and headlines are unhandled here");

                            try {
                                processMessage_(m);
                            } catch (ExFormatError e) {
                                logProcessingError_("pl: badly formatted message", e, packet);
                            } catch (ExProtocolError e) {
                                logProcessingError_("pl: unrecognized message", e, packet);
                            } catch (Exception e) {
                                logProcessingError_("pl: cannot process valid message", e, packet);

                                // we fatal for a number of reasons here:
                                // - an exception from disconnect_ within a subsystem
                                //   is unrecoverable. It means that we're trying
                                //   to recover from an error condition, but our
                                //   recovery process is failing. in this case we
                                //   really shouldn't go on
                                // - we cannot reschedule this event because ordering
                                //   is extremely important in processing XMPP
                                //   messages. Receiving online, offline messages
                                //   is very different than offline, online

                                SystemUtil.fatal(e);
                            }
                        }
                    }
                }, Prio.LO);
            }
        }, new MessageTypeFilter(Message.Type.normal));

        super.xmppServerConnected(conn);
    }

    /**
     * Processes an encoded XMPP message from a peer.
     *
     * @param m XMPP message to decode and process
     * @throws ExFormatError if the JID cannot be converted to a DID
     * @throws ExProtocolError if the header is an unrecognized (i.e. unhandled) type
     * @throws ExNoResource if the message can't be processed by an
     * {@link ISignallingClient} due to resource constraints
     */
    private void processMessage_(Message m) throws ExFormatError, ExProtocolError, ExNoResource
    {
        assertDispThread();

        DID did = ID.jid2did(m.getFrom());
        PBTPHeader hdr;
        try {
            OutArg<Integer> wirelen = new OutArg<Integer>(0);
            byte[] decoded = decodeBody(did, wirelen, m.getBody());
            if (decoded == null) return;
            hdr = PBTPHeader.parseFrom(decoded);
        } catch (IOException e) {
            l.warn(Util.e(e));
            return;
        }

        PBTPHeader.Type type = hdr.getType();
        l.debug("rcv msg type:" + hdr.getType().name());

        ISignallingClient mp = _processors.get(type);
        if (mp == null) throw new ExProtocolError(((Object) type).getClass()); // stupid cast for IDEA 12
        mp.processSignallingMessage_(did, hdr);
    }

    private void logProcessingError_(String errmsg, Exception e, Packet packet)
    {
        l.warn(errmsg + " from:" + packet.getFrom() + " err: " + Util.e(e));
    }

    //
    // members
    //

    private final Map<Type, ISignallingClient> _processors = newHashMap();
}
