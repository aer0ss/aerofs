package com.aerofs.daemon.core.syncstatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.DID2User;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.status.EIGetSyncStatus;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Ritual.PBSyncStatus;
import com.google.inject.Inject;

public class HdGetSyncStatus extends AbstractHdIMC<EIGetSyncStatus>
{
    private final DirectoryService _ds;
    private final DID2User _did2user;
    private final LocalSyncStatus _lsync;

    @Inject
    public HdGetSyncStatus(DirectoryService ds, DID2User did2user, LocalSyncStatus lsync)
    {
        _ds = ds;
        _did2user = did2user;
        _lsync = lsync;
    }

    /**
     * Aggregate sync status for multiple devices belonging to the same user.
     * @param base Latest aggregated status
     * @param extra Extra status to aggregate
     * @return aggregated status
     */
    private PBSyncStatus.Status aggregateSyncStatus(PBSyncStatus.Status base,
                                                    PBSyncStatus.Status extra)
    {
        if (base == null)
            base = PBSyncStatus.Status.OFFLINE;
        // NOTE: this relies on the ordering of enum values in common.proto
        return base.compareTo(extra) < 0 ? base : extra;
    }

    /**
     * Retrieve sync status for a given path, group "foreign" devices by owner and make
     * it human-readable by turning device and user IDs into name whenever possible.
     */
    @Override
    protected void handleThrows_(EIGetSyncStatus ev, Prio prio)
            throws Exception
    {
        SOID soid = _ds.resolveThrows_(ev.getPath());
        Map<DID, PBSyncStatus.Status> m = _lsync.getSyncStatusMap_(soid);

        List<PBSyncStatus> result = new ArrayList<PBSyncStatus>();
        Map<String, PBSyncStatus.Status> aggregated = new TreeMap<String, PBSyncStatus.Status>();
        for (Entry<DID, PBSyncStatus.Status> e : m.entrySet()) {
            String user = _did2user.getFromLocalNullable_(e.getKey());
            if (user == null) continue;
            if (user.equals(Cfg.user())) {
                result.add(PBSyncStatus.newBuilder()
                                       .setUserName(user)
                                       .setDeviceName(e.getKey().toStringFormal())
                                       .setStatus(e.getValue())
                                       .build());
            } else {
                aggregated.put(user, aggregateSyncStatus(aggregated.get(user), e.getValue()));
            }
        }

        for (Entry<String, PBSyncStatus.Status> e : aggregated.entrySet()) {
            // TODO: name resolution
            //FullName fn = _udndb.getUserNameNullable_(e.getKey());
            result.add(PBSyncStatus.newBuilder()
                                   .setUserName(e.getKey())
                                   .setStatus(e.getValue())
                                   .build());
        }

        ev.setResult_(result);
    }
}
