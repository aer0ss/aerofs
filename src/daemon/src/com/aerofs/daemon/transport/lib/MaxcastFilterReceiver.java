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
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class MaxcastFilterReceiver {
    private static final Logger l = Loggers.getLogger(MaxcastFilterReceiver.class);

    private final Map<DID, Integer> _didToMaxcastIds;

    public MaxcastFilterReceiver()
    {
        _didToMaxcastIds = new HashMap<DID, Integer>();
    }

    public boolean isRedundant(DID did, int mcastid)
    {
        boolean retVal;

        synchronized (this) {
            retVal = isRedundant_(did, mcastid);
        }

        if (l.isDebugEnabled()) {
            l.debug( ((retVal) ? ("redundant") : ("unique")) + " mc recvd: " + did + " " + mcastid);
        }

        return retVal;
    }

    private boolean isRedundant_(DID did, int mcastid)
    {
        assert did != null;

        Integer oldmcid = _didToMaxcastIds.put(did, mcastid);
        if (oldmcid == null || oldmcid.compareTo(mcastid) != 0) {
            return false;
        }

        return true;
    }
}
