/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp.presence;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocationReceiver;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.daemon.transport.presence.ExInvalidPresenceLocation;
import com.aerofs.daemon.transport.presence.IStoreInterestListener;
import com.aerofs.daemon.transport.presence.PresenceLocationFactory;
import com.aerofs.ids.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.ids.SID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.google.common.collect.*;
import com.google.gson.*;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Presence;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import static com.aerofs.base.id.JabberID.muc2sid;
import static com.aerofs.base.id.JabberID.user2did;
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
public final class XMPPPresenceProcessor implements IXMPPConnectionServiceListener
{
    private static final Logger l = Loggers.getLogger(XMPPPresenceProcessor.class);

    private final Multimap<DID, SID> multicastReachableDevices = TreeMultimap.create(); // protected by 'this' TODO (AG): should be SID => DID*

    private final DID localdid;
    private final String xmppServerDomain;
    private final IMulticastListener multicastListener;
    private final IStoreInterestListener storeInterestListener;
    private final IPresenceLocationReceiver presenceLocationReceiver;

    /**
     * Constructor
     */
    public XMPPPresenceProcessor(DID localdid, String xmppServerDomain,
            IMulticastListener multicastListener, IStoreInterestListener storeInterestListener,
            IPresenceLocationReceiver presenceLocationReceiver)
    {
        this.localdid = localdid;
        this.xmppServerDomain = xmppServerDomain;
        this.multicastListener = multicastListener;
        this.storeInterestListener = storeInterestListener;
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
                    l.warn("{} fail process presence", packet.getFrom(),
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

        if (!JabberID.isMUCAddress(jidComponents, xmppServerDomain)) return false;

        SID sid = muc2sid(jidComponents[0]);
        DID did = user2did(jidComponents[1]);

        if (did.equals(localdid)) return false;

        return updateStores(presence.isAvailable(), did, sid);
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
                presenceLocations.add(location);
            } catch (ExInvalidPresenceLocation e) {
                l.info("dropped location {} for did {} ({})", jsonPresenceLocation.toString(), did, e.getMessage());
            }
        }

        return presenceLocations;
    }

    private boolean updateStores(boolean available, DID did, SID sid) {
        boolean deviceTransition, storeTransition;
        l.debug("{} process {} for {}", did, available ? "online" : "offline", sid);

        // lock carefully: monitor protects maps, never held while calling listeners
        // NOTE: On the other hand. Apparently all notifications are going to come via this
        // XMPP thread. So no out-of-order notifications should occur
        synchronized (this) {
            if (available) {
                deviceTransition = !multicastReachableDevices.containsKey(did);
                storeTransition = multicastReachableDevices.put(did, sid);
            } else {
                // if remove does nothing, the DID:SID map did not exist; bail out early if so
                if (!multicastReachableDevices.remove(did, sid)) {
                    return true;
                }
                storeTransition = true;
                deviceTransition = !multicastReachableDevices.containsKey(did);
            }
        }

        // handle multicast state transitions:
        if (deviceTransition) {
            l.info("{} recv {} presence for {}", did, available ? "online" : "offline", sid);
            if (available) {
                multicastListener.onDeviceReachable(did);
            } else {
                multicastListener.onDeviceUnreachable(did);
            }
        }

        if (storeTransition) {
            if (available) {
                storeInterestListener.onDeviceJoin(did, sid);
            } else {
                storeInterestListener.onDeviceLeave(did, sid);
            }
        }
        return true;
    }
}
