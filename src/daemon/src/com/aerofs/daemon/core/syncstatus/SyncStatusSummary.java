package com.aerofs.daemon.core.syncstatus;

import com.aerofs.daemon.core.store.DeviceBitMap;
import com.aerofs.daemon.core.syncstatus.LocalSyncStatus.IAggregatedStatus;
import com.aerofs.daemon.lib.db.UserAndDeviceNames;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.DID;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * IAggregatedStatus implementation for summary aggregation
 *
 * Devices not not belonging to the local users are aggregated by user using OR (i.e if at least
 * one of its devices is in sync the user is considered in sync) before computing the summary.
 */
public class SyncStatusSummary implements IAggregatedStatus
{
    private final UserAndDeviceNames _udn;
    private final CfgLocalUser _user;

    public boolean atLeastOneInSync = false;
    public boolean allInSync = true;

    public SyncStatusSummary(UserAndDeviceNames udn, CfgLocalUser user)
    {
        _udn = udn;
        _user = user;
    }

    @Override
    public IAggregatedStatus create()
    {
        return new SyncStatusSummary(_udn, _user);
    }

    @Override
    public void mergeDevices_(DeviceBitMap dbm, BitVector status)
    {
        Map<String, Boolean> otherUsers = Maps.newHashMap();
        for (int i = 0; i < dbm.size(); ++i) {
            boolean s = status.test(i);
            String owner = getOwner_(dbm.get(i));
            if (owner.equals(_user.get())) {
                atLeastOneInSync |= s;
                allInSync &= s;
            } else {
                Boolean b = otherUsers.get(owner);
                otherUsers.put(owner, (b != null ? b | s : s));
            }
        }

        for (boolean s : otherUsers.values()) {
            atLeastOneInSync |= s;
            allInSync &= s;
        }
    }

    @Override
    public void mergeStore_(IAggregatedStatus aggregated)
    {
        SyncStatusSummary o = (SyncStatusSummary)aggregated;
        // aggregation accross children is always AND (take worst)
        atLeastOneInSync &= o.atLeastOneInSync;
        allInSync &= o.allInSync;
    }

    private String getOwner_(DID did)
    {
        String owner = null;
        try {
            owner = _udn.getDeviceOwnerNullable_(did);
        } catch (Exception e) {
            Util.l(this).warn("owner lookup failed: " + did, e);
        }
        return owner == null ? "(Unknown)" : owner;
    }
}
