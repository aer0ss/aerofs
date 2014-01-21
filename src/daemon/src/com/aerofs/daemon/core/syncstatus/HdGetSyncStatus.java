package com.aerofs.daemon.core.syncstatus;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus;
import com.aerofs.daemon.core.serverstatus.ServerConnectionStatus.Server;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.status.EIGetSyncStatus;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.UserAndDeviceNames.DeviceInfo;
import com.aerofs.lib.FullName;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.base.id.DID;
import com.aerofs.lib.id.SOID;
import com.aerofs.base.id.UserID;
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
    private final ServerConnectionStatus _scs;

    @Inject
    public HdGetSyncStatus(CfgLocalUser localUser, DirectoryService ds, DevicePresence dp,
            ServerConnectionStatus scs, UserAndDeviceNames didinfo, LocalSyncStatus lsync)
    {
        _localUser = localUser;
        _ds = ds;
        _dp = dp;
        _scs = scs;
        _didinfo = didinfo;
        _lsync = lsync;
    }

    /**
     * Adjust sync status based on device presence (online/offline status)
     */
    private Status presenceAwareStatus(DID did, boolean status)
    {
        if (status) return Status.IN_SYNC;
        return _dp.getOPMDevice_(did) != null ? Status.IN_PROGRESS : Status.OFFLINE;
    }

    /**
     * Aggregate sync status for multiple devices belonging to the same user.
     * Keeps the "best" status
     */
    private static Status bestSyncStatus(@Nullable Status a, Status b)
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

        // do not show sync status when any of the servers is known to be down
        if (!_scs.isConnected(Server.SYNCSTAT, Server.VERKEHR)) {
            ev.setResult_(false, result);
            return;
        }

        SOID soid = _ds.resolveThrows_(ev.getPath());

        OA oa = _ds.getOA_(soid);
        if (oa.isExpelled()) throw new ExExpelled(ev.getPath().toString() + " is expelled");

        // map (DID -> sync status)
        Map<DID, Boolean> syncStatus = _lsync.getFullyAggregatedSyncStatusMap_(soid);

        // map (DID -> device name and user name)
        Map<DID, DeviceInfo> deviceInfo = _didinfo.getDeviceInfoMap_(syncStatus.keySet());
        // map (foreign user id -> sync status aggregated across all devices of that user)
        Map<UserID, PBSyncStatus.Status> aggregated = Maps.newTreeMap();

        // first round: add devices owned by local user to result and aggregate foreign devices
        for (Entry<DID, Boolean> e : syncStatus.entrySet()) {
            DID did = e.getKey();
            Status status = presenceAwareStatus(did, e.getValue());
            DeviceInfo info = deviceInfo.get(did);
            if (info == null) continue;
            UserID userID = info.owner._userId;
            if (userID.equals(_localUser.get())) {
                result.add(PBSyncStatus.newBuilder()
                        .setStatus(status)
                        .setUserID(userID.getString())
                        .setDisplayName(info.deviceName != null ? info.deviceName
                                : did.toStringFormal())
                        .build());
            } else {
                aggregated.put(info.owner._userId,
                               bestSyncStatus(aggregated.get(info.owner._userId), status));
            }
        }

        // second round add aggregated foreign devices to result
        for (Entry<UserID, Status> e : aggregated.entrySet()) {
            UserID userID = e.getKey();
            FullName fn = _didinfo.getUserNameNullable_(userID);
            result.add(PBSyncStatus.newBuilder()
                    .setStatus(e.getValue())
                    .setUserID(userID.getString())
                    .setDisplayName(fn != null ? fn.getString() : userID.getString())
                    .build());
        }

        ev.setResult_(true, result);
    }
}
