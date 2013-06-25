/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.mobile.MobileServerZephyrConnector;
import com.aerofs.daemon.transport.lib.ITransportStats.BasicStatsCounter;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.xmpp.zephyr.netty.ZephyrConnectionService;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.rocklog.RockLog;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;

import java.io.IOException;
import java.net.Proxy;
import java.net.SocketAddress;
import java.util.Set;

import static com.aerofs.base.id.JabberID.did2FormAJid;
import static com.aerofs.daemon.transport.lib.TPUtil.registerMulticastHandler;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.newHashSet;
import static org.jivesoftware.smack.packet.Message.Type.chat;
import static org.jivesoftware.smack.packet.Message.Type.error;
import static org.jivesoftware.smack.packet.Message.Type.groupchat;
import static org.jivesoftware.smack.packet.Message.Type.headline;

public class Zephyr extends XMPP implements ISignallingService
{
    private final boolean enableMulticast;
    private final Set<ISignallingClient> signallingClients = newHashSet();
    private final MobileServerZephyrConnector mobileZephyrConnector;

    public Zephyr(
            UserID localid,
            DID localdid,
            String id, int rank,
            IBlockingPrioritizedEventSink<IEvent> sink,
            MaxcastFilterReceiver mcfr,
            SSLEngineFactory clientSSLEngineFactory,
            ClientSocketChannelFactory clientSocketChannelFactory,
            MobileServerZephyrConnector mobileZephyr,
            RockLog rocklog,
            SocketAddress zephyrAddress, Proxy proxy,
            boolean enableMulticast)
    {
        super(localdid, id, rank, sink, mcfr);

        checkState(DaemonParam.XMPP.CONNECT_TIMEOUT > DaemonParam.Zephyr.HANDSHAKE_TIMEOUT); // should be much larger!

        ZephyrConnectionService pipe = new ZephyrConnectionService(
                new BasicIdentifier(id, rank),
                localid, localdid,
                clientSSLEngineFactory,
                this,
                this,
                new BasicStatsCounter(),
                rocklog,
                clientSocketChannelFactory, zephyrAddress, proxy);
        setPipe_(pipe);
        mobileZephyrConnector = mobileZephyr;

        l.debug("{}: mc enable:{}", id, enableMulticast);

        this.enableMulticast = enableMulticast;
        if (enableMulticast) {
            registerMulticastHandler(this);
        }
    }

    @Override
    public boolean supportsMulticast()
    {
        return enableMulticast;
    }

    //--------------------------------------------------------------------------
    //
    //
    // ISignallingService methods (how subsystems can send/receive messages via
    // a control channel)
    //
    //
    //--------------------------------------------------------------------------

    // FIXME (AG): completely refactor how signalling messages are sent via XMPP (see IQ)

    @Override
    public void registerSignallingClient(ISignallingClient client)
    {
        checkState(!ready(), "z: already started");
        signallingClients.add(client);
    }

    @Override
    public void sendSignallingMessage(final DID did, final byte[] msg, final ISignallingClient client)
    {
        OutArg<Integer> len = new OutArg<Integer>(0);
        String enc = XMPP.encodeBody(len, msg);

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
            notifySignallingClientOfError(client, did, msg, e);
        } catch (IllegalStateException e) {
            // NOTE: this can happen because smack considers it illegal to attempt to send
            // a packet if the channel is not connected. Since we may be notified of a
            // disconnection after actually enqueuing the packet to be sent, it's entirely
            // possible for this to occur
            notifySignallingClientOfError(client, did, msg, e);
        }
    }

    private void notifySignallingClientOfError(ISignallingClient client, DID did, byte[] msg, Exception e)
    {
        try {
            client.sendSignallingMessageFailed(did, msg, e);
        } catch (ExNoResource e2) {
            l.error("x: failed to handle error while sending packet");
            SystemUtil.fatal("shutdown due to err:" + e2 + " triggered by err:" + e);
        }
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
                                // - an exception from disconnect within a subsystem
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

        if (mobileZephyrConnector != null) {
            mobileZephyrConnector.setConnection(conn);
        }

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

        try {
            DID did = JabberID.jid2did(m.getFrom());
            OutArg<Integer> wirelen = new OutArg<Integer>(0);
            byte[] decoded = decodeBody(did, wirelen, m.getBody());
            if (decoded == null) return;

            for (ISignallingClient client : signallingClients) {
                client.processIncomingSignallingMessage(did, decoded);
            }
        } catch (IOException e) {
            l.warn(Util.e(e));
            return;
        }
    }

    private void logProcessingError_(String errmsg, Exception e, Packet packet)
    {
        l.warn(errmsg + " from:" + packet.getFrom() + " err: " + Util.e(e));
    }
}
