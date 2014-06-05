/**
 * MaxcastFilterReceiver
 * @author markj
 * With MaxcastFilterSender, this pair of classes assigns a unique maxcast ID
 * to each maxcast packet that is sent over multiple transports in TransportRoutingLayer.
 * Duplicate/redundant packets will inevitably be received by each peer for
 * each network interface/transport. Thus, upon receiving a maxcast payload
 * packet, each transport must check whether that particular mcast ID has
 * already been received from the given device ID. The method "isReundant"
 * below provides this functionality by storing <DID, int> pairs in a private
 * map, and checking if the pair already exists.
 *
 * Shortcomings:
 * - Mcast IDs are packaged into the EOMaxcastMessage class, so we do not filter
 *   packets sent over many unicasts (see TransportRoutingLayer layer).
 * - In "isRedundant" we only check for equality of mcast ID's, so if packets
 *   arrive out of order, they will not be filtered away. This is not incorrect,
 *   it simply results in occasionally unfiltered packets.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import java.util.Map;

public class MaxcastFilterReceiver
{
    private static final Logger l = Loggers.getLogger(MaxcastFilterReceiver.class);

    private final Map<DID, Integer> lastReceivedMaxcastId = Maps.newHashMap();

    public boolean isRedundant(DID did, int maxcastId)
    {
        boolean retVal;

        synchronized (this) {
            retVal = isRedundantInternal(did, maxcastId);
        }

        l.debug("{} {} mc {}", did, retVal ? "redundant" : "unique", maxcastId);

        return retVal;
    }

    private boolean isRedundantInternal(DID did, int maxcastId)
    {
        Integer previousMaxcastId = lastReceivedMaxcastId.put(did, maxcastId);

        if (previousMaxcastId == null || previousMaxcastId.compareTo(maxcastId) != 0) {
            return false;
        }

        return true;
    }
}
