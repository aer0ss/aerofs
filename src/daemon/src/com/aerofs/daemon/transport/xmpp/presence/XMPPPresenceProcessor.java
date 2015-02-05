/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp.presence;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID.ExInvalidID;
import com.aerofs.daemon.event.net.EIStoreAvailability;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IDevicePresenceListener;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Set;

import static com.aerofs.base.id.JabberID.muc2sid;
import static com.aerofs.base.id.JabberID.user2did;
import static com.aerofs.lib.event.Prio.LO;
import static com.aerofs.lib.log.LogUtil.suppress;

/**
 * For XMPP-using transports, this class manages the mapping of devices to stores, and generates
 * Core presence events as needed.
 *
 * Presence updates are sent:
 *
 *  - when a device comes online on unicast; this class is registered as a device presence listener.
 *  In this case, presence notification lists all SIDs that are currently known for that device.
 *
 *  - when a device-to-SID mapping is updated and the device is currently online. In this case the
 *  presence update contains only the changes to the device-to-SID map.
 *
 * An XMPP packet listener keeps the private did-to-sid map up to date.
 *
 * FIXME: When the XMPP connection goes offline, we do not clear the did-to-sid map.
 * I'm assuming we will _not_ get presence packets for those situations.
 * Worry: what if the did2sid map is out of date?
 *
 * Oh, one more responsibility:  This class also feeds the multicast listener machinery.
 */
public final class XMPPPresenceProcessor implements IXMPPConnectionServiceListener, IDevicePresenceListener
{
    private static final Logger l = Loggers.getLogger(XMPPPresenceProcessor.class);

    private final Multimap<DID, SID> multicastReachableDevices = TreeMultimap.create(); // protected by 'this' TODO (AG): should be SID => DID*
    private final Set<DID> onlineDevices = Sets.newHashSet();

    private final DID localdid;
    private final String xmppServerDomain;
    private final IMulticastListener multicastListener;
    private final String transportId;
    private final ITransport transport;
    private final IBlockingPrioritizedEventSink<IEvent> outgoingEventSink;

    /**
     * Constructor
     */
    public XMPPPresenceProcessor(DID localdid, String xmppServerDomain, ITransport transport,
            IBlockingPrioritizedEventSink<IEvent> outgoingEventSink,
            IMulticastListener multicastListener)
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
                        l.warn("{} fail process presence over {}", packet.getFrom(), transportId,
                                suppress(e, ExInvalidID.class));
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
     * @throws ExInvalidID if any required field cannot be parsed
     */
    boolean processPresenceForUnitTests(Presence presence)
            throws ExInvalidID
    {
        return processPresence(presence);
    }

    // return 'true' if processed, 'false' otherwise
    // the return aids in unit tests
    private boolean processPresence(Presence presence) throws ExInvalidID
    {
        if (l.isTraceEnabled()) l.trace("receive presence p:{}", presence.toXML());

        String[] jidComponents = JabberID.tokenize(presence.getFrom());

        if (JabberID.isMobileUser(jidComponents[1])) return false;
        if (!JabberID.isMUCAddress(jidComponents, xmppServerDomain)) return false;
        if (jidComponents.length == 3 && (jidComponents[2].compareToIgnoreCase(transportId) != 0)) return false;

        SID sid = muc2sid(jidComponents[0]);
        DID did = user2did(jidComponents[1]);

        return (did.equals(localdid)) ? false : updateStores(presence.isAvailable(), did, sid);
    }

    private boolean updateStores(boolean available, DID did, SID sid)
    {
        boolean deviceTransition, deviceOnline;
        l.debug("{} process {} for {} on {}", did, available ? "online" : "offline", sid, transportId);

        // lock carefully: monitor protects maps, never held while calling listeners
        // NOTE: On the other hand. Apparently all notifications are going to come via this
        // XMPP thread. So no out-of-order notifications should occur
        synchronized (this) {
            deviceOnline = onlineDevices.contains(did);

            if (available) {
                deviceTransition = !multicastReachableDevices.containsKey(did);
                multicastReachableDevices.put(did, sid);
            } else {
                // if remove does nothing, the DID:SID map did not exist; bail out early if so
                if (!multicastReachableDevices.remove(did, sid)) { return true; }
                deviceTransition = !multicastReachableDevices.containsKey(did);
            }
        }

        // handle multicast state transitions:
        if (deviceTransition) {
            l.info("{} recv {} presence for {} on {}", did, available ? "online" : "offline", sid, transportId);
            if (available) {
                multicastListener.onDeviceReachable(did);
            } else {
                multicastListener.onDeviceUnreachable(did);
            }
        }

        // handle core notifications, sent only for connected devices:
        if (deviceOnline) {
            outgoingEventSink.enqueueBlocking(new EIStoreAvailability(transport, available, did, ImmutableList.of(sid)), LO);
        }

        return true;
    }

    @Override
    public void onDevicePresenceChanged(DID did, boolean isPotentiallyAvailable)
    {
        Collection<SID> onlineStores;

        // update onlineDevices and make a snapshot of the reachable devices for the given DID
        // lock carefully: monitor protects maps, never held while calling listeners
        synchronized (this) {
            boolean b = isPotentiallyAvailable ? onlineDevices.add(did) : onlineDevices.remove(did);
            onlineStores = ImmutableSet.copyOf(multicastReachableDevices.get(did));
        }

        if (onlineStores != null && !onlineStores.isEmpty()) {
            outgoingEventSink.enqueueBlocking(new EIStoreAvailability(transport, isPotentiallyAvailable, did, onlineStores), LO);
        }
    }
}
