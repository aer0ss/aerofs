/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp.signalling;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.slf4j.Logger;

import java.io.IOException;

import static com.aerofs.base.id.JabberID.did2FormAJid;
import static com.aerofs.daemon.transport.xmpp.XMPPUtilities.decodeBody;
import static com.aerofs.daemon.transport.xmpp.XMPPUtilities.encodeBody;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jivesoftware.smack.packet.Message.Type.chat;
import static org.jivesoftware.smack.packet.Message.Type.error;
import static org.jivesoftware.smack.packet.Message.Type.groupchat;
import static org.jivesoftware.smack.packet.Message.Type.headline;

// FIXME (AG): completely refactor how signalling messages are sent via XMPP (see IQ)
public final class SignallingService implements ISignallingService, IXMPPConnectionServiceListener
{
    private static final Logger l = Loggers.getLogger(SignallingService.class);

    private final String transportId;
    private final String xmppServerDomain;

    private volatile XMPPConnection xmppConnection;
    private ISignallingServiceListener client;

    public SignallingService(String transportId, String xmppServerDomain, XMPPConnectionService xmppConnectionService)
    {
        this.transportId = transportId;
        this.xmppServerDomain = xmppServerDomain;

        xmppConnectionService.addListener(this); // FIXME (AG): bad to leak this out via the constructor
    }

    @Override
    public void registerSignallingClient(ISignallingServiceListener client)
    {
        checkState(this.client == null);
        this.client = client;
    }

    @Override
    public void sendSignallingMessage(DID did, byte[] msg, ISignallingServiceListener client)
    {
        OutArg<Integer> len = new OutArg<Integer>(0);
        String enc = encodeBody(len, msg);

        final Message xmsg = new Message(did2FormAJid(did, xmppServerDomain, transportId), Message.Type.normal);
        xmsg.setBody(enc);

        XMPPConnection currentConnection = xmppConnection;

        if (currentConnection == null) {
            notifyZephyrConnectionServiceOfSignallingFailure(client, did, msg, new IOException("no connection to signalling service"));
            return;
        }

        try {
            currentConnection.sendPacket(xmsg);
        } catch (IllegalStateException e) {
            // NOTE: this can happen because smack considers it illegal to attempt to send
            // a packet if the channel is not connected. Since we may be notified of a
            // disconnection _after_ trying to send a packet, we have to handle this
            notifyZephyrConnectionServiceOfSignallingFailure(client, did, msg, e);
        }
    }

    private void notifyZephyrConnectionServiceOfSignallingFailure(ISignallingServiceListener client, DID did, byte[] msg, Exception e)
    {
        client.sendSignallingMessageFailed(did, msg, e);
    }

    @Override
    public void xmppServerConnected(XMPPConnection xmppConnection)
            throws XMPPException
    {
        l.info("register packet listeners");

        xmppConnection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                if (packet instanceof Message) {
                    Message m = (Message)packet;

                    if (m.getSubject() != null) return;

                    Message.Type type = m.getType();
                    checkArgument(type != groupchat && type != headline && type != chat && type != error, "unexpected type:%s", type.name());

                    try {
                        processMessage(m);
                    } catch (ExInvalidID e) {
                        logXmppProcessingError(packet, "unrecognized message", e);
                    } catch (Exception e) {
                        logXmppProcessingError(packet, "fail process valid signalling message", e);
                    }
                }
            }

            private void processMessage(Message m)
                    throws ExInvalidID
            {
                try {
                    DID did = JabberID.jid2did(m.getFrom(), xmppServerDomain);
                    OutArg<Integer> wirelen = new OutArg<Integer>(0);
                    byte[] decoded = decodeBody(did, wirelen, m.getBody(), null);
                    if (decoded == null) return;
                    client.processIncomingSignallingMessage(did, decoded);
                } catch (IOException e) {
                    l.warn(Util.e(e));
                }
            }

            private void logXmppProcessingError(Packet packet, String message, Exception cause)
            {
                l.warn("{} fail process {}", packet.getFrom(), message, cause);
            }
        }, new MessageTypeFilter(Message.Type.normal));

        this.xmppConnection = xmppConnection;

        client.signallingServiceConnected();
    }

    @Override
    public void xmppServerDisconnected()
    {
        this.xmppConnection = null;
        client.signallingServiceDisconnected();
    }
}
