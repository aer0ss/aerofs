package com.aerofs.daemon.core.status;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.net.device.Devices.DeviceAvailabilityListener;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.google.common.collect.Lists;

import org.slf4j.Logger;

import javax.inject.Inject;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class listens for device online status in order to keep track of whether
 * or not sync status is enabled. Sync status is disabled when there are no
 * reachable storage agent devices.
 */
public class SyncStatusPresenceListener implements DeviceAvailabilityListener
{
    private final static Logger l = Loggers.getLogger(SyncStatusPresenceListener.class);

    private final SyncStatusPropagator _syncStatusPropagator;
    private final SyncStatusOnline _syncStatusOnline;
    private final UserAndDeviceNames _uadn;

    private final Set<DID> reachableStorageAgentDevices;

    @Inject
    public SyncStatusPresenceListener(SyncStatusPropagator syncStatusPropagator,
            SyncStatusOnline syncStatusOnline, UserAndDeviceNames uadn, Devices devices) {
        this._syncStatusPropagator = syncStatusPropagator;
        this._syncStatusOnline = syncStatusOnline;
        this.reachableStorageAgentDevices = ConcurrentHashMap.newKeySet();
        this._uadn = uadn;

        devices.addListener_(this);
    }

    @Override
    public void online_(DID did) {
        l.trace("online: {}", did);
        updateAvailableStorageAgentDIDs_(did, true);
    }

    @Override
    public void offline_(DID did) {
        l.trace("offline_: {}", did);
        updateAvailableStorageAgentDIDs_(did, false);
    }

    private void updateAvailableStorageAgentDIDs_(DID did, boolean isPotentiallyAvailable) {
        try {
            UserID user = _uadn.getDeviceOwnerNullable_(did);
            if (user == null && _uadn.updateLocalDeviceInfo_(Lists.newArrayList(did))) {
                user = _uadn.getDeviceOwnerNullable_(did);
            }
            l.trace("updateAvailableStorageAgentDIDs_ user: {}", user);
            if (user == null || !user.isTeamServerID()) return;
        } catch (SQLException | ExProtocolError e) {
            l.error("error updating available storage agents", e);
            return;
        }

        if (isPotentiallyAvailable) {
            reachableStorageAgentDevices.add(did);
        } else {
            reachableStorageAgentDevices.remove(did);
        }

        boolean syncStatusEnabled = reachableStorageAgentDevices.size() > 0;
        if (_syncStatusOnline.set(syncStatusEnabled)) {
            l.trace("updateAvailableStorageAgentDIDs_: changed");
            try {
                _syncStatusPropagator.notifyRootSyncStatus_();
            } catch (SQLException e) {
                l.error("error notifying root sync status", e);;
            }
        }
    }
}
