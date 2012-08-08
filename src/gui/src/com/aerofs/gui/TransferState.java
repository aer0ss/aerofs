package com.aerofs.gui;

import java.util.Map;

import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.google.common.collect.Maps;

public class TransferState
{
    // these two fields are protected by synchronized (this)
    private final Map<SOCID, PBDownloadEvent> _dls = Maps.newHashMap();
    private final Map<SOCID, PBUploadEvent> _uls = Maps.newHashMap();

    private final boolean _onlyTrackOngoingDownloads;

    public TransferState(boolean onlyTrackOngoingDownloads)
    {
        _onlyTrackOngoingDownloads = onlyTrackOngoingDownloads;
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
        if (pb.getDone() == pb.getTotal()) _uls.remove(socid);
        else _uls.put(socid, pb);
    }

    @SuppressWarnings("fallthrough")
    private void updateDownloadState_(PBDownloadEvent pb)
    {
        switch (pb.getState()) {
        default:
            if (_onlyTrackOngoingDownloads) break;
            // fall through
        case ONGOING:
            _dls.put(new SOCID(pb.getSocid()), pb);
            break;
        case ENDED:
            _dls.remove(new SOCID(pb.getSocid()));
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
    public Map<SOCID, PBUploadEvent> uploads_()
    {
        return _uls;
    }
}
