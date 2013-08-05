/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IUnicastInternal;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.log.LogUtil;
import com.google.common.collect.ImmutableMap;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.slf4j.Logger;

import java.util.Collection;

public class XMPPPresenceManager implements IXMPPConnectionServiceListener
{
    private static final Logger l = Loggers.getLogger(XMPPPresenceManager.class);

    private final ITransport _tp;
    private final DID _localdid;
    private final IBlockingPrioritizedEventSink<IEvent> _sink;
    private final PresenceStore _presenceStore;
    private final PulseManager _pulseManager;
    private final IUnicastInternal _unicast;

    public XMPPPresenceManager(ITransport tp, DID localdid,
            IBlockingPrioritizedEventSink<IEvent> sink, PresenceStore presenceStore,
            PulseManager pulseManager, IUnicastInternal unicast)
    {
        _tp = tp;
        _localdid = localdid;
        _sink = sink;
        _presenceStore = presenceStore;
        _pulseManager = pulseManager;
        _unicast = unicast;
    }

    @Override
    public void xmppServerDisconnected()
    {
        l.warn("x: xsc noticed disconnect");
        _sink.enqueueBlocking(new EIPresence(_tp, false, ImmutableMap.<DID, Collection<SID>>of()), Prio.LO);
    }

    @Override
    public void xmppServerConnected(final XMPPConnection conn) throws XMPPException
    {
        final PacketTypeFilter presfilter = new PacketTypeFilter(Presence.class);

        conn.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(final Packet packet)
            {
                if (packet instanceof Presence) {
                    try {
                        processPresence((Presence)packet);
                    } catch (Exception e) {
                        l.warn("pl: cannot process_ mc presence from {}:", packet.getFrom(),
                                LogUtil.suppress(e, ExFormatError.class));
                    }
                }
            }
        }, presfilter);
    }

    private void processPresence(Presence p)
            throws ExFormatError
    {
        String[] tokens = JabberID.tokenize(p.getFrom());
        if (!JabberID.isMUCAddress(tokens)) return;
        if (JabberID.isMobileUser(tokens[1])) return;
        // ignore presence from other xmpp-based transports
        if (tokens.length == 3 && (tokens[2].compareToIgnoreCase(_tp.id()) != 0)) return;

        SID sid = JabberID.muc2sid(tokens[0]);
        DID did = JabberID.user2did(tokens[1]);
        if (did.equals(_localdid)) return;

        if (p.isAvailable()) {
            l.info("{}: recv online presence d:{}", _tp.id(), did);
            _presenceStore.add(did, sid);
            _pulseManager.stopPulse(did, false);

        } else {
            boolean waslast = _presenceStore.remove(did, sid);
            if (waslast) {
                l.info("{}: recv offline presence d:{}", _tp.id(), did);
                _pulseManager.stopPulse(did, false);
                _unicast.disconnect(did, new Exception("remote offline"));
            }
        }

        _sink.enqueueBlocking(new EIPresence(_tp, p.isAvailable(), did, sid), Prio.LO);
    }
}
