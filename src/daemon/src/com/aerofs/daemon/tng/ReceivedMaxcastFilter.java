/*
 * Copyright (c) Air Computing Inc., 2012.
 */

/**
 * ReceivedMaxcastFilter
 * @author markj
 * With MaxcastFilterSender, this pair of classes assigns a unique maxcast ID
 * to each maxcast packet that is sent over multiple transports in NSL.
 * Duplicate/redundant packets will inevitably be received by each peer for
 * each network interface/transport. Thus, upon receiving a maxcast payload
 * packet, each transport must check whether that particular mcast ID has
 * already been received from the given device ID. The method "isReundant"
 * below provides this functionality by storing <DID, int> pairs in a private
 * map, and checking if the pair already exists.
 *
 * Shortcomings:
 * - Mcast IDs are packaged into the EOMaxcastMessage class, so we do not filter
 *   packets sent over many unicasts (see NSL layer).
 * - In "isRedundant" we only check for equality of mcast ID's, so if packets
 *   arrive out of order, they will not be filtered away. This is not incorrect,
 *   it simply results in occasionally unfiltered packets.
 */

package com.aerofs.daemon.tng;

import com.aerofs.lib.Util;
import com.aerofs.lib.id.DID;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class ReceivedMaxcastFilter
{
    private static final Logger l = Util.l(ReceivedMaxcastFilter.class);
    // Map of DID to maxcast IDs
    private final Map<DID, Integer> _mmcids;

    public ReceivedMaxcastFilter()
    {
        _mmcids = new HashMap<DID, Integer>();
    }

    public boolean isRedundant(DID did, int mcastid)
    {
        boolean retVal;
        synchronized (this) {
            retVal = isRedundant_(did, mcastid);
        }
        if (l.isInfoEnabled()) {
            l.info(((retVal) ? ("redundant") : ("unique")) + " mcast recvd: " + did + " " +
                    mcastid);
        }
        return retVal;
    }

    private boolean isRedundant_(DID did, int mcastid)
    {
        assert did != null;

        Integer oldmcid = _mmcids.put(did, mcastid);
        if (oldmcid == null || oldmcid.compareTo(mcastid) != 0) {
            return false;
        }
        return true;
    }
}
