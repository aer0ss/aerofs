package com.aerofs.gui;

import java.util.Map;

import com.aerofs.base.id.DID;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.google.common.base.Predicate;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

public class TransferState
{
    // these two fields are protected by synchronized (this)
    private final Map<SOCID, PBDownloadEvent> _dls = Maps.newHashMap();
    private final Table<SOCID, DID, PBUploadEvent> _uls = HashBasedTable.create();

    private final boolean _onlyTrackOngoingDownloads;
    private boolean _trackMetaDataTransfers = false;

    private final Predicate<SOCID> _isMeta = new Predicate<SOCID>() {
        @Override
        public boolean apply(SOCID socid)
        {
            return socid.cid().isMeta();
        }
    };

    public TransferState(boolean onlyTrackOngoingDownloads)
    {
        _onlyTrackOngoingDownloads = onlyTrackOngoingDownloads;
    }

    public void enableTrackingMetaData(boolean enable)
    {
        _trackMetaDataTransfers = enable;

        // we should have some meta-data entries if we were tracking them,
        //   and we will have to remove those entries when we disable
        //   meta-data tracking
        if (!_trackMetaDataTransfers) {
            // use guava because:
            // 1. it's a lot more readable
            // 2. it uses Iterator correctly to avoid ConcurrentModificationException
            Iterables.removeIf(_dls.keySet(), _isMeta);
            Iterables.removeIf(_uls.rowKeySet(), _isMeta);
        }
    }

    /**
     * Nop if the notification type is not download or upload
     */
    public synchronized void update(PBNotification pb)
    {
        switch (pb.getType()) {
        case DOWNLOAD:
            updateDownloadState_(pb.getDownload());
            break;
        case UPLOAD:
            updateUploadState_(pb.getUpload());
            break;
        default:
            // no-op
        }
    }

    private void updateUploadState_(PBUploadEvent pb)
    {
        SOCID socid = new SOCID(pb.getSocid());
        DID did = new DID(pb.getDeviceId());

        if (!_trackMetaDataTransfers && socid.cid().equals(CID.META)) return;

        if (pb.getDone() == pb.getTotal()) _uls.remove(socid, did);
        else _uls.put(socid, did, pb);
    }

    @SuppressWarnings("fallthrough")
    private void updateDownloadState_(PBDownloadEvent pb)
    {
        SOCID socid = new SOCID(pb.getSocid());

        if (!_trackMetaDataTransfers && socid.cid().equals(CID.META)) return;

        switch (pb.getState()) {
        default:
            if (_onlyTrackOngoingDownloads) break;
            // fall through
        case ONGOING:
            _dls.put(socid, pb);
            break;
        case ENDED:
            _dls.remove(socid);
            break;
        }
    }

    /**
     * N.B. access to the return value must be protected by synchronized (this)
     */
    public Map<SOCID, PBDownloadEvent> downloads_()
    {
        return _dls;
    }

    /**
     * N.B. access to the return value must be protected by synchronized (this)
     */
    public Table<SOCID, DID, PBUploadEvent> uploads_()
    {
        return _uls;
    }
}
