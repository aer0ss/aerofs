package com.aerofs.daemon.core.syncstatus;

import com.aerofs.daemon.core.store.DeviceBitMap;
import com.aerofs.daemon.core.syncstatus.LocalSyncStatus.IAggregatedStatus;
import com.aerofs.daemon.lib.db.UserAndDeviceNames;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UserID;
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

    private boolean _isPartiallySynced = false;
    private boolean _allInSync = true;

    public SyncStatusSummary(UserAndDeviceNames udn, CfgLocalUser user)
    {
        _udn = udn;
        _user = user;
    }

    public boolean isPartiallySynced()
    {
        return _isPartiallySynced;
    }

    public boolean allInSync()
    {
        return _allInSync;
    }

    @Override
    public IAggregatedStatus create()
    {
        return new SyncStatusSummary(_udn, _user);
    }

    @Override
    public void mergeDevices_(DeviceBitMap dbm, BitVector status)
    {
        Map<UserID, Boolean> otherUsers = Maps.newHashMap();
        for (int i = 0; i < dbm.size(); ++i) {
            boolean s = status.test(i);
            UserID owner = getOwner_(dbm.get(i));
            if (owner.equals(_user.get())) {
                _isPartiallySynced |= s;
                _allInSync &= s;
            } else {
                Boolean b = otherUsers.get(owner);
                otherUsers.put(owner, (b != null ? b | s : s));
            }
        }

        for (boolean s : otherUsers.values()) {
            _isPartiallySynced |= s;
            _allInSync &= s;
        }
    }

    @Override
    public void mergeStore_(IAggregatedStatus aggregated)
    {
        SyncStatusSummary o = (SyncStatusSummary)aggregated;
        // aggregation accross children is always AND (take worst)
        _isPartiallySynced &= o._isPartiallySynced;
        _allInSync &= o._allInSync;
    }

    private UserID getOwner_(DID did)
    {
        UserID owner = UserID.UNKNOWN;
        try {
            owner = _udn.getDeviceOwnerNullable_(did);
        } catch (Exception e) {
            Util.l(this).warn("owner lookup failed: " + did, e);
        }
        return owner;
    }
}
