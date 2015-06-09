/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp.presence;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocationReceiver;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.daemon.transport.presence.ExInvalidPresenceLocation;
import com.aerofs.daemon.transport.presence.PresenceLocationFactory;
import com.aerofs.ids.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.ids.SID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.daemon.event.net.EIStoreAvailability;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IDevicePresenceListener;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.google.common.collect.*;
import com.google.gson.*;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Presence;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
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
    private final IPresenceLocationReceiver presenceLocationReceiver;

    /**
     * Constructor
     */
    public XMPPPresenceProcessor(DID localdid, String xmppServerDomain, ITransport transport,
            IBlockingPrioritizedEventSink<IEvent> outgoingEventSink,
            IMulticastListener multicastListener, IPresenceLocationReceiver presenceLocationReceiver)
    {
        this.localdid = localdid;
        this.xmppServerDomain = xmppServerDomain;
        this.transportId = transport.id();
        this.transport = transport;
        this.outgoingEventSink = outgoingEventSink;
        this.multicastListener = multicastListener;
        this.presenceLocationReceiver = presenceLocationReceiver;
    }

    @Override
    public void xmppServerConnected(final XMPPConnection connection) throws XMPPException
    {
        connection.addPacketListener(packet -> {
            if (packet instanceof Presence) {
                try {
                    processPresence((Presence)packet, connection);
                } catch (Exception e) {
                    l.warn("{} fail process presence over {}", packet.getFrom(), transportId,
                            suppress(e, ExInvalidID.class));
                }
            }
        }, new PacketTypeFilter(Presence.class));

        multicastListener.onMulticastReady();
    }

    @Override
    public void xmppServerDisconnected()
    {
        multicastListener.onMulticastUnavailable();
        synchronized (this) {
            for (Entry<DID, SID> e : ImmutableList.copyOf(multicastReachableDevices.entries())) {
                updateStores(false, e.getKey(), e.getValue());
            }
        }
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
        return processPresence(presence, null);
    }

    // return 'true' if processed, 'false' otherwise
    // the return aids in unit tests
    private boolean processPresence(Presence presence, @Nullable XMPPConnection connection)
            throws ExInvalidID
    {
        if (l.isTraceEnabled()) l.trace("receive presence p:{}", presence.toXML());

        String[] jidComponents = JabberID.tokenize(presence.getFrom());

        if (JabberID.isMobileUser(jidComponents[1])) return false;
        if (!JabberID.isMUCAddress(jidComponents, xmppServerDomain)) return false;
        if (jidComponents.length == 3 && (jidComponents[2].compareToIgnoreCase(transportId) != 0)) return false;

        SID sid = muc2sid(jidComponents[0]);
        DID did = user2did(jidComponents[1]);

        if (did.equals(localdid)) return false;

        boolean updated = updateStores(presence.isAvailable(), did, sid);

        // TODO: retrieve and process vCard asynchronously to avoid interference with zephyr
        if (presence.isAvailable()) {
            @Nullable String metadata = fetchVCard(connection, presence.getFrom());
            if (metadata != null && !metadata.isEmpty()) {
                l.info("Found metadata for {}: {}", did, metadata);
                // Parse it
                Set<IPresenceLocation> presenceLocations = parseMetadata(did, metadata);
                // Notify it
                presenceLocations.forEach(presenceLocationReceiver::onPresenceReceived);
            }
        }

        return updated;
    }

    /**
     * Parse the metadata and extract the valid presence locations.
     *
     * @param did the metadata belongs to this DID
     * @param metadata the metadata, as a Json list of locations
     * @return the set of found presence locations
     */
    private Set<IPresenceLocation> parseMetadata(DID did, String metadata)
    {
        JsonParser jsonParser = new JsonParser();
        JsonArray jsonPresenceList = jsonParser.parse(metadata).getAsJsonArray();

        HashSet<IPresenceLocation> presenceLocations = new HashSet<>();

        if (jsonPresenceList == null) {
            l.info("empty location list, dropping");
            return presenceLocations;
        }

        for (JsonElement jsonPresenceLocation : jsonPresenceList) {
            try {
                IPresenceLocation location = PresenceLocationFactory.fromJson(did, (JsonObject) jsonPresenceLocation);
                if (location.transportType().getId().equals(transport.id())) {
                    // DESIGN constraint:
                    // XMPPPresenceProcessor is instantiated twice, once for each transport (TCP, Zephyr).
                    // Each instance will notify its transport-specific instance of IPresenceReceiver,
                    //  so we don't want to notify locations that are not for the current transport.
                    presenceLocations.add(location);
                }
            } catch (ExInvalidPresenceLocation e) {
                l.info("dropped location {} for did {} ({})", jsonPresenceLocation.toString(), did, e.getMessage());
            }
        }

        return presenceLocations;
    }

    /**
     * Retrieve a vCard metadata for a given JID
     *
     * @param jid the JID of the user we want the metadata
     * @return The Metadata String, or an empty string if an error occurred
     */
    private static @Nullable String fetchVCard(@Nullable XMPPConnection connection, String jid)
    {
        // should only be null for tests
        if (connection == null) return null;
        try {
            XMPPvCard card = new XMPPvCard();
            // Read the given vCard
            card.load(connection, jid);
            return card.readMetadata();
        } catch (Throwable e) {
            l.warn("Unable to retrieve the vCard for JID {}", jid, e);
            return null;
        }
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
