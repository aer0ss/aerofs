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
import com.google.common.collect.ImmutableList;
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

    private final Multimap<DID, SID> multicastReachableDevices = TreeMultimap.create(); // protected by 'this' TODO (AG): should be SID => DID*
    private final DID localdid;
    private final String xmppServerDomain;
    private final IMulticastListener multicastListener;
    private final String transportId;
    private final ITransport transport;
    private final IBlockingPrioritizedEventSink<IEvent> outgoingEventSink;

    /**
     * Constructor
     */
    public XMPPPresenceProcessor(DID localdid, String xmppServerDomain, ITransport transport, IBlockingPrioritizedEventSink<IEvent> outgoingEventSink, IMulticastListener multicastListener)
    {
        this.localdid = localdid;
        this.xmppServerDomain = xmppServerDomain;
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
                        processPresence((Presence)packet);
                    } catch (Exception e) {
                        l.warn("{} fail process presence over {}", packet.getFrom(), transportId, suppress(e, ExFormatError.class));
                    }
                }
            }
        }, new PacketTypeFilter(Presence.class));

        multicastListener.onMulticastReady();
    }

    @Override
    public void xmppServerDisconnected()
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
    boolean processPresenceForUnitTests(Presence presence)
            throws ExFormatError
    {
        return processPresence(presence);
    }

    // return 'true' if processed, 'false' otherwise
    // the return aids in unit tests
    // lock must _not_ be held while notifying listeners!
    private boolean processPresence(Presence presence)
            throws ExFormatError
    {
        l.trace("receive presence p:{}", presence.toXML());

        String[] jidComponents = JabberID.tokenize(presence.getFrom());

        if (JabberID.isMobileUser(jidComponents[1])) return false;
        if (!JabberID.isMUCAddress(jidComponents, xmppServerDomain)) return false;
        if (jidComponents.length == 3 && (jidComponents[2].compareToIgnoreCase(transportId) != 0)) return false;

        SID sid = muc2sid(jidComponents[0]);
        DID did = user2did(jidComponents[1]);

        if (did.equals(localdid)) {
            return false;
        }

        l.debug("{} process presence for {} on {}", did, sid, transportId);

        if (presence.isAvailable()) {

            boolean newDid;
            synchronized (this) { // this lock should _only_ be around modifications to multicastReachableDevices
                newDid = !multicastReachableDevices.containsKey(did);
                multicastReachableDevices.put(did, sid);
            }

            if (newDid) {
                l.info("{} recv online presence for {} on {}", did, sid, transportId);
                multicastListener.onDeviceReachable(did);
            }
        } else {

            boolean removedDid;
            synchronized (this) {  // this lock should _only_ be around modifications to multicastReachableDevices
                // always remove the did->sid pair first
                // and _then_ check whether the did was completely removed
                boolean removedMapping = multicastReachableDevices.remove(did, sid);

                // apparently this DID, SID pair doesn't exist
                // since it doesn't we'll bail early to avoid sending spurious notifications
                if (!removedMapping) {
                    return true;
                }

                removedDid = !multicastReachableDevices.containsKey(did);
            }

            if (removedDid) {
                l.info("{} recv offline presence for {} on {}", did, sid, transportId);
                multicastListener.onDeviceUnreachable(did);
            }
        }

        outgoingEventSink.enqueueBlocking(new EIPresence(transport, presence.isAvailable(), did, ImmutableList.of(sid)), LO);
        return true;
    }

    @Override
    public void onDevicePresenceChanged(DID did, boolean isPotentiallyAvailable)
    {
        if (isPotentiallyAvailable) return;

        Collection<SID> previouslyOnlineStores;
        synchronized (this) { // this lock should _only_ be around modifications to multicastReachableDevices
            previouslyOnlineStores = multicastReachableDevices.removeAll(did);
        }

        if (previouslyOnlineStores != null && !previouslyOnlineStores.isEmpty()) {
            outgoingEventSink.enqueueBlocking(new EIPresence(transport, false, did, previouslyOnlineStores), LO);
        }
    }
}
