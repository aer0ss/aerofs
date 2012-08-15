package com.aerofs.daemon.core.syncstatus;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.status.EIGetSyncStatus;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.UserAndDeviceNames;
import com.aerofs.daemon.lib.db.UserAndDeviceNames.DeviceInfo;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Ritual.PBSyncStatus;
import com.aerofs.proto.Ritual.PBSyncStatus.Status;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import javax.annotation.Nullable;


public class HdGetSyncStatus extends AbstractHdIMC<EIGetSyncStatus>
{
    private final CfgLocalUser _localUser;
    private final UserAndDeviceNames _didinfo;
    private final DirectoryService _ds;
    private final LocalSyncStatus _lsync;
    private final DevicePresence _dp;

    // TODO(huguesb): cache results of recursive aggregation

    @Inject
    public HdGetSyncStatus(CfgLocalUser localUser, DirectoryService ds, DevicePresence dp,
            UserAndDeviceNames didinfo, LocalSyncStatus lsync)
    {
        _localUser = localUser;
        _ds = ds;
        _dp = dp;
        _didinfo = didinfo;
        _lsync = lsync;
    }

    /**
     * Adjust sync status based on device presence (online/offline status)
     */
    private Status presenceAwareStatus(DID did, Status status)
    {
        if (status == Status.IN_SYNC) return status;
        return _dp.getOPMDevice_(did) != null ? Status.IN_PROGRESS : Status.OFFLINE;
    }

    /**
     * Aggregate sync status for multiple files in a directory.
     * Keeps the "worst" status
     */
    private Status worstSyncStatus(@Nullable Status a, Status b) {
        if (a == null) return b;
        switch (a) {
        case IN_SYNC:
            return b;
        case IN_PROGRESS:
            return b == Status.IN_SYNC ? a : b;
        case OFFLINE:
            return a;
        }
        assert false;
        return a;
    }

    /**
     * Aggregate sync status for multiple devices belonging to the same user.
     * Keeps the "best" status
     */
    private Status bestSyncStatus(@Nullable Status a, Status b)
    {
        if (a == null) return b;
        switch (a) {
        case IN_SYNC:
            return a;
        case IN_PROGRESS:
            return b == Status.OFFLINE ? a : b;
        case OFFLINE:
            return b;
        }
        assert false;
        return a;
    }

    /**
     * Sync status is known at device granularity, however, to avoid exposing too much complexity in
     * the UI, foreign devices are grouped by user id and their status aggregated using a simple
     * rule (if at least one device is in sync the user is considered in sync, ...)
     *
     * This method performs that grouping and also makes the information human-readable by turning
     * DIDs into device names and user names.
     */
    @Override
    protected void handleThrows_(EIGetSyncStatus ev, Prio prio) throws Exception
    {
        // result of ritual call
        List<PBSyncStatus> result = Lists.newArrayList();

        SOID soid = _ds.resolveThrows_(ev.getPath());

        // map (DID -> sync status)
        Map<DID, PBSyncStatus.Status> syncStatus = aggregateSyncStatusRecursively_(soid);

        // map (DID -> device name and user name)
        Map<DID, DeviceInfo> deviceInfo = _didinfo.getDeviceInfoMap_(syncStatus.keySet());
        // map (foreign user id -> sync status aggregated across all devices of that user)
        Map<String, PBSyncStatus.Status> aggregated = Maps.newTreeMap();

        // first round: add devices owned by local user to result and aggregate foreign devices
        for (Entry<DID, PBSyncStatus.Status> e : syncStatus.entrySet()) {
            DeviceInfo info = deviceInfo.get(e.getKey());
            if (info == null) continue;
            if (info.owner.userId.equals(_localUser.get())) {
                result.add(PBSyncStatus.newBuilder()
                        .setUserName(info.owner.getName())
                        .setDeviceName(info.deviceName != null ? info.deviceName : e.getKey()
                                .toStringFormal())
                        .setStatus(presenceAwareStatus(e.getKey(), e.getValue()))
                        .build());
            } else {
                aggregated.put(info.owner.userId,
                               bestSyncStatus(aggregated.get(info.owner.userId),
                                              presenceAwareStatus(e.getKey(), e.getValue())));
            }
        }

        // second round add aggregated foreign devices to result
        for (Entry<String, PBSyncStatus.Status> e : aggregated.entrySet()) {
            FullName fn = _didinfo.getUserNameNullable_(e.getKey());
            result.add(PBSyncStatus.newBuilder()
                    .setUserName(fn != null ? fn.combine() : e.getKey())
                    .setStatus(e.getValue())
                    .build());
        }

        ev.setResult_(result);
    }

    /**
     * Aggregate sync status recursively for directories, with caching
     */
    private Map<DID, PBSyncStatus.Status> aggregateSyncStatusRecursively_(SOID soid)
            throws Exception {
        OA oa = _ds.getOA_(soid);
        Map<DID, PBSyncStatus.Status> aggregated;
        if (oa.isAnchor()) {
            aggregated = _lsync.getSyncStatusMap_(soid);
            aggregateWorst(aggregated,
                    aggregateSyncStatusRecursively_(_ds.followAnchorNullable_(oa)));
        } else if (oa.isDir()) {
            // TODO(huguesb): cache lookup
            aggregated = null;

            if (aggregated == null) {
                Util.l(this).warn("cache miss " + soid.toString());
                // cache miss : must re-aggregate sync status of children

                if (soid.oid().isRoot()) {
                    // sync status of root directories is always empty
                    aggregated = Maps.newHashMap();
                } else {
                    aggregated = _lsync.getSyncStatusMap_(soid);
                }

                for (OID oid : _ds.getChildren_(soid)) {
                    if (!oid.equals(OID.TRASH)) {
                        aggregateWorst(aggregated,
                                aggregateSyncStatusRecursively_(new SOID(soid.sidx(), oid)));
                    }
                }

                // TODO(huguesb): update cache
            }
        } else {
            aggregated = _lsync.getSyncStatusMap_(soid);
        }
        return aggregated;
    }

    /**
     * Aggregate two maps using {@link #worstSyncStatus}
     */
    private void aggregateWorst(Map<DID, PBSyncStatus.Status> aggregated,
                                Map<DID, PBSyncStatus.Status> child) {
        for (Entry<DID, PBSyncStatus.Status> e : child.entrySet()) {
            aggregated.put(e.getKey(),
                           worstSyncStatus(aggregated.get(e.getKey()), e.getValue()));
        }
    }
}
