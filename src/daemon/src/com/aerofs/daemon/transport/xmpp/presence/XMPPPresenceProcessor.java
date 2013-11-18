/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp.presence;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IDevicePresenceListener;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.slf4j.Logger;

import java.util.Collection;

import static com.aerofs.base.id.JabberID.muc2sid;
import static com.aerofs.base.id.JabberID.user2did;
import static com.aerofs.lib.event.Prio.LO;
import static com.aerofs.lib.log.LogUtil.suppress;

/**
 * Maintains a mapping of stores a remote device belongs to (i.e. DID => SID*).
 * It updates this mapping by processing XMPP packets from our XMPP server, and notifies the
 * core whenever this mapping changes.
 */
public final class XMPPPresenceProcessor implements IXMPPConnectionServiceListener, IDevicePresenceListener
{
    private static final Logger l = Loggers.getLogger(XMPPPresenceProcessor.class);

    private final Multimap<DID, SID> multicastReachableDevices = TreeMultimap.create(); // TODO (AG): should be SID => DID*
    private final DID localdid;
    private final IMulticastListener multicastListener;
    private final String transportId;
    private final ITransport transport;
    private final IBlockingPrioritizedEventSink<IEvent> outgoingEventSink;

    /**
     * Constructor
     *
     * @param outgoingEventSink queue into which notifications to the core will be placed
     */
    public XMPPPresenceProcessor(DID localdid, ITransport transport, IBlockingPrioritizedEventSink<IEvent> outgoingEventSink, IMulticastListener multicastListener)
    {
        this.localdid = localdid;
        this.transportId = transport.id();
        this.transport = transport;
        this.outgoingEventSink = outgoingEventSink;
        this.multicastListener = multicastListener;
    }

    @Override
    public void xmppServerConnected(final XMPPConnection connection) throws XMPPException
    {
        connection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(final Packet packet)
            {
                if (packet instanceof Presence) {
                    try {
                        synchronized (XMPPPresenceProcessor.this) {
                            processPresence((Presence)packet);
                        }
                    } catch (Exception e) {
                        l.warn("{}: fail process presence from {}:", transportId, packet.getFrom(), suppress(e, ExFormatError.class));
                    }
                }
            }
        }, new PacketTypeFilter(Presence.class));

        multicastListener.onMulticastReady();
    }

    @Override
    public synchronized void xmppServerDisconnected()
    {
        multicastListener.onMulticastUnavailable();
    }

    /**
     * Process an presence packet
     * <p/>
     * This method is meant to be used in unit tests only. It is identical to the method
     * called within the {@link org.jivesoftware.smack.PacketListener} added to an
     * {@link org.jivesoftware.smack.XMPPConnection}.
     *
     * @param presence {@link org.jivesoftware.smack.packet.Presence} packet to process
     * @return true if the packet was processed, false otherwise
     * @throws ExFormatError if any required field cannot be parsed
     */
    synchronized boolean processPresenceForUnitTests(Presence presence)
            throws ExFormatError
    {
        return processPresence(presence);
    }

    // return 'true' if processed, 'false' otherwise
    // the return aids in unit tests
    private boolean processPresence(Presence presence)
            throws ExFormatError
    {
        String[] jidComponents = JabberID.tokenize(presence.getFrom());

        // if the presence is not from a store
        // or, from a mobile user,
        // or, it's presence from another XMPP-based transport
        // then, ignore it
        if (!JabberID.isMUCAddress(jidComponents)
                || JabberID.isMobileUser(jidComponents[1])
                || (jidComponents.length == 3 && (jidComponents[2].compareToIgnoreCase(transportId) != 0))) {
            return false;
        }

        SID sid = muc2sid(jidComponents[0]);
        DID did = user2did(jidComponents[1]);

        l.debug("process presence d:{} s:{}", did, sid);

        if (did.equals(localdid)) {
            return false;
        }

        if (presence.isAvailable()) {
            if (!multicastReachableDevices.containsKey(did)) {
                l.info("{}: recv online presence d:{}", transportId, did);
                multicastListener.onDeviceReachable(did);
            }
            multicastReachableDevices.put(did, sid);
        } else {
            boolean removed = multicastReachableDevices.remove(did, sid);

            // apparently this DID, SID pair doesn't exist
            // since it doesn't we'll bail early to avoid sending spurious notifications
            if (!removed) {
                return true;
            }

            if (!multicastReachableDevices.containsKey(did)) {
                l.info("{}: recv offline presence d:{}", transportId, did);
                multicastListener.onDeviceUnreachable(did);
            }
        }

        outgoingEventSink.enqueueBlocking(new EIPresence(transport, presence.isAvailable(), did, sid), LO);
        return true;
    }

    @Override
    public synchronized void onDevicePresenceChanged(DID did, boolean isPotentiallyAvailable)
    {
        if (isPotentiallyAvailable) return;

        Collection<SID> previouslyOnlineStores = multicastReachableDevices.removeAll(did);
        if (previouslyOnlineStores != null && !previouslyOnlineStores.isEmpty()) {
            outgoingEventSink.enqueueBlocking(new EIPresence(transport, false, did, previouslyOnlineStores), LO);
        }
    }
}
