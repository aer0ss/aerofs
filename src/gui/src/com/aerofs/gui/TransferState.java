package com.aerofs.gui;

import java.util.Map;

import com.aerofs.base.id.DID;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

public class TransferState
{
    // these two fields are protected by synchronized (this)
    private final Map<SOCID, PBDownloadEvent> _dls = Maps.newHashMap();
    private final Table<SOCID, DID, PBUploadEvent> _uls = HashBasedTable.create();

    private final boolean _onlyTrackOngoingDownloads;
    private boolean _trackMetaDataTransfers = false;

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
            for (SOCID socid : _dls.keySet()) {
                if (socid.cid().equals(CID.META)) {
                    _dls.remove(socid);
                }
            }

            for (SOCID socid : _uls.rowKeySet()) {
                if (socid.cid().equals(CID.META)) {
                    for (DID did : _uls.row(socid).keySet()) {
                        _uls.remove(socid, did);
                    }
                }
            }
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
